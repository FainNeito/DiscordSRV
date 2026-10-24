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

package github.scarsz.discordsrv.account;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.commands.PluginSlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommand;
import github.scarsz.discordsrv.api.commands.SlashCommandProvider;
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.dv8tion.jda.api.events.interaction.ButtonClickEvent;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.events.message.priv.PrivateMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.Button;
import org.bukkit.Bukkit;
import java.time.Clock;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class AccountSelfServiceListener extends ListenerAdapter implements SlashCommandProvider {
    private final DiscordSRV plugin;
    private final AccountSelfService service;
    public AccountSelfServiceListener(DiscordSRV plugin) {
        this.plugin = plugin;
        this.service = new AccountSelfService(new AccountSelfService.Store() {
            public UUID current(String actor) { return plugin.getAccountLinkManager().getUuidForSelfService(actor); }
            public boolean remove(String actor, UUID expected) throws Exception {
                return plugin.getAccountLinkManager().unlinkIfMatches(actor, expected);
            }
        }, Clock.systemUTC());
    }
    @Override public Set<PluginSlashCommand> getSlashCommands() {
        return Collections.singleton(new PluginSlashCommand(plugin,
                new CommandData("account", "Privately view or unlink your Minecraft account")));
    }
    @SlashCommand(path = "account", deferReply = true, deferEphemeral = true)
    public void account(SlashCommandEvent event) {
        async(() -> {
            try {
                UUID uuid = service.current(event.getUser().getId());
                if (uuid == null) event.getHook().editOriginal(instructions()).queue();
                else event.getHook().editOriginal(status(uuid)).setActionRow(
                        Button.danger("dsrv-account:review", "Unlink account")).queue();
            } catch (Exception e) {
                DiscordSRV.error("Account status lookup failed", e);
                event.getHook().editOriginal("Account service is unavailable. Please try again later.").queue();
            }
        });
    }
    public boolean privateMessage(PrivateMessageReceivedEvent event) {
        String text = event.getMessage().getContentRaw().trim();
        if (!text.equalsIgnoreCase("account") && !text.equalsIgnoreCase("unlink")) return false;
        async(() -> {
            try {
                UUID uuid = service.current(event.getAuthor().getId());
                if (uuid == null) event.getMessage().reply(instructions()).queue();
                else event.getMessage().reply(status(uuid)).setActionRow(
                        Button.danger("dsrv-account:review", "Unlink account")).queue();
            } catch (Exception e) {
                DiscordSRV.error("Account status lookup failed", e);
                event.getMessage().reply("Account service is unavailable. Please try again later.").queue();
            }
        });
        return true;
    }
    @Override public void onButtonClick(ButtonClickEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("dsrv-account:")) return;
        event.deferEdit().queue(hook -> async(() -> {
            String actor = event.getUser().getId();
            try {
                if (id.equals("dsrv-account:review")) {
                    UUID uuid = service.current(actor);
                    if (uuid == null) { hook.editOriginal(instructions()).setActionRows(Collections.emptyList()).queue(); return; }
                    String token = service.offer(actor, uuid);
                    hook.editOriginal("**Unlink " + name(uuid) + "?**\n" +
                            "This removes your Minecraft–Discord link and can remove linked-only roles or server access. " +
                            "Your Minecraft items and progress are not deleted. You can link another account afterward.\n" +
                            "Confirm within 2 minutes. Only your own Discord link can be removed.")
                        .setActionRow(Button.danger("dsrv-account:confirm:" + token, "Confirm unlink"),
                                Button.secondary("dsrv-account:cancel:" + token, "Keep linked")).queue();
                } else if (id.startsWith("dsrv-account:confirm:")) {
                    AccountSelfService.Result result = service.confirm(actor, id.substring("dsrv-account:confirm:".length()));
                    String reply;
                    switch (result) {
                        case UNLINKED: reply = "**Account unlinked.**\n" + instructions() +
                                "\nIf your Discord roles or access do not update correctly, contact staff."; break;
                        case STALE: reply = "Your account link changed. Open /account or DM account again before unlinking."; break;
                        case EXPIRED: reply = "This confirmation expired or was already used. Open /account or DM account again."; break;
                        case DENIED: reply = "This confirmation belongs to another member."; break;
                        default: reply = "The unlink outcome could not be confirmed. Open /account or DM account to check your link before retrying.";
                    }
                    hook.editOriginal(reply).setActionRows(Collections.emptyList()).queue();
                } else if (id.startsWith("dsrv-account:cancel:")) {
                    service.cancel(actor, id.substring("dsrv-account:cancel:".length()));
                    hook.editOriginal("Unlink cancelled. Open /account or DM account to check your current link.")
                        .setActionRows(Collections.emptyList()).queue();
                }
            } catch (Exception e) {
                DiscordSRV.error("Account self-service failed", e);
                hook.editOriginal("Account service is unavailable. Check /account before retrying.")
                        .setActionRows(Collections.emptyList()).queue();
            }
        }));
    }
    private void async(Runnable task) { SchedulerUtil.runTaskAsynchronously(plugin, task); }
    private static String name(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }
    private static String status(UUID uuid) {
        return "**Your Minecraft account: " + name(uuid) + "**\n" +
                "Lost access to that Minecraft account? You can unlink here using this Discord account.\n" +
                "Choose Unlink account to review and confirm.";
    }
    private static String instructions() {
        return "**Link your Minecraft account**\n1. Join Minecraft on the account you want to use.\n" +
                "2. Run `/discord link`.\n3. Send the fresh code in a DM to this bot from the Discord account you want linked.\n" +
                "If Minecraft is already linked to a different Discord, unlink there or ask staff for recovery.";
    }
}
