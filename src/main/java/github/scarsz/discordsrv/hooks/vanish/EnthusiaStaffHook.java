/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.hooks.vanish;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.PluginUtil;
import github.scarsz.discordsrv.hooks.PluginHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Adapter over EnthusiaStaff's authoritative, read-only Bukkit visibility service. */
public final class EnthusiaStaffHook implements VanishHook {
    private static final UUID PUBLIC_AUDIENCE = new UUID(0, 0);
    private static final String SERVICE = "net.enthusia.staff.paper.api.StaffVisibilityService";
    private final AtomicBoolean degraded = new AtomicBoolean();
    private final Consumer<String> warning;

    public EnthusiaStaffHook() {
        this(DiscordSRV::warning);
    }

    EnthusiaStaffHook(Consumer<String> warning) {
        this.warning = warning;
    }

    public static boolean replacesSuperVanish(String candidate, Collection<PluginHook> hooks) {
        return candidate.equals("github.scarsz.discordsrv.hooks.vanish.SuperVanishHook")
                && hooks.stream().anyMatch(hook -> hook instanceof EnthusiaStaffHook);
    }

    @Override
    public Plugin getPlugin() {
        return PluginUtil.getPlugin("EnthusiaStaff");
    }

    @Override
    public boolean isEnabled() {
        // A present-but-disabled Staff plugin must retain a fail-closed guard.
        return selected(getPlugin() != null, DiscordSRV.config().getStringList("DisabledPluginHooks"));
    }

    static boolean selected(boolean present, Collection<String> disabledHooks) {
        return present && disabledHooks.stream().noneMatch(name ->
                "enthusiastaff".startsWith(name.toLowerCase(Locale.ROOT)));
    }

    @Override
    public boolean isVanished(Player player) {
        return querySafely(() -> {
            Plugin staff = getPlugin();
            if (staff == null || !staff.isEnabled()) {
                throw new IllegalStateException("Staff provider disabled or unloaded");
            }
            Class<?> api = Class.forName(SERVICE, false, staff.getClass().getClassLoader());
            Object provider = Bukkit.getServicesManager().load(api);
            if (provider == null) {
                throw new IllegalStateException("Staff visibility service unavailable");
            }
            return publicHidden(api, provider, player.getUniqueId());
        });
    }

    interface Query {
        boolean hidden() throws ReflectiveOperationException;
    }

    boolean querySafely(Query query) {
        try {
            boolean hidden = query.hidden();
            degraded.set(false);
            return hidden;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (degraded.compareAndSet(false, true)) {
                warning.accept("EnthusiaStaff visibility unavailable; hiding players from public Discord surfaces until it recovers.");
            }
            return true;
        }
    }

    static boolean publicHidden(Class<?> api, Object provider, UUID subject) throws ReflectiveOperationException {
        Method vanished = api.getMethod("isVanished", UUID.class);
        Method canSee = api.getMethod("canSee", UUID.class, UUID.class);
        // This synthetic audience is never registered with Staff as a staff viewer.
        return PUBLIC_AUDIENCE.equals(subject) || (boolean) vanished.invoke(provider, subject)
                || !(boolean) canSee.invoke(provider, PUBLIC_AUDIENCE, subject);
    }
}
