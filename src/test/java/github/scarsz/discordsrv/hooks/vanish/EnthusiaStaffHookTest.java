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

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;

class EnthusiaStaffHookTest {
    public interface VisibilityApi {
        boolean isVanished(UUID subject);
        boolean canSee(UUID viewer, UUID subject);
    }

    private VisibilityApi provider(boolean vanished, boolean dutyHidden) {
        return new VisibilityApi() {
            public boolean isVanished(UUID subject) { return vanished; }
            public boolean canSee(UUID viewer, UUID subject) { return !dutyHidden; }
        };
    }

    @Test void visiblePlayerRemainsVisible() throws Exception {
        assertFalse(EnthusiaStaffHook.publicHidden(VisibilityApi.class, provider(false, false), UUID.randomUUID()));
    }

    @Test void onlyReplaceSuperVanishAfterStaffGuardExists() {
        String legacy = "github.scarsz.discordsrv.hooks.vanish.SuperVanishHook";
        assertFalse(EnthusiaStaffHook.replacesSuperVanish(legacy, Collections.emptyList()));
        assertTrue(EnthusiaStaffHook.replacesSuperVanish(legacy, Collections.singletonList(new EnthusiaStaffHook(message -> { }))));
        assertFalse(EnthusiaStaffHook.replacesSuperVanish("github.scarsz.discordsrv.hooks.vanish.EssentialsHook", Collections.singletonList(new EnthusiaStaffHook(message -> { }))));
    }

    @Test void selectionHonorsExplicitHookDisableButNotProviderStartupState() {
        assertTrue(EnthusiaStaffHook.selected(true, Collections.emptyList()));
        assertFalse(EnthusiaStaffHook.selected(false, Collections.emptyList()));
        assertFalse(EnthusiaStaffHook.selected(true, Collections.singletonList("ENTHUSIASTAFF")));
        assertFalse(EnthusiaStaffHook.selected(true, Collections.singletonList("Enthusia")));
        assertTrue(EnthusiaStaffHook.selected(true, Collections.singletonList("SuperVanish")));
    }

    @Test void blankDisableEntriesCannotRemoveThePrivacyGuard() {
        assertTrue(EnthusiaStaffHook.selected(true, Collections.singletonList("")));
        assertTrue(EnthusiaStaffHook.selected(true, Collections.singletonList("   ")));
        assertTrue(EnthusiaStaffHook.selected(true, Collections.singletonList(null)));
        assertFalse(EnthusiaStaffHook.selected(true, java.util.Arrays.asList("", " EnthusiaStaff ")));
    }

    @Test void vanishedPlayerIsHidden() throws Exception {
        assertTrue(EnthusiaStaffHook.publicHidden(VisibilityApi.class, provider(true, false), UUID.randomUUID()));
    }

    @Test void staffDutyWithoutVanishIsHidden() throws Exception {
        assertTrue(EnthusiaStaffHook.publicHidden(VisibilityApi.class, provider(false, true), UUID.randomUUID()));
    }

    @Test void reservedPublicIdentityIsNeverExposed() throws Exception {
        assertTrue(EnthusiaStaffHook.publicHidden(VisibilityApi.class, provider(false, false), new UUID(0, 0)));
    }

    @Test void providerFailuresDoNotKillWorkersOrFloodLogs() {
        AtomicInteger warnings = new AtomicInteger();
        EnthusiaStaffHook hook = new EnthusiaStaffHook(message -> warnings.incrementAndGet());
        assertTrue(hook.querySafely(() -> { throw new ReflectiveOperationException(); }));
        assertTrue(hook.querySafely(() -> { throw new IllegalStateException(); }));
        assertTrue(hook.querySafely(() -> { throw new NoClassDefFoundError(); }));
        assertEquals(1, warnings.get());
        assertFalse(hook.querySafely(() -> false));
        assertTrue(hook.querySafely(() -> { throw new IllegalStateException(); }));
        assertEquals(2, warnings.get());
    }

    @Test void unknownApiIsFailClosed() {
        EnthusiaStaffHook hook = new EnthusiaStaffHook(message -> { });
        assertTrue(hook.querySafely(() -> EnthusiaStaffHook.publicHidden(Object.class, new Object(), UUID.randomUUID())));
    }
}
