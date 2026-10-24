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
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.time.Clock;
import java.util.UUID;

/** Self-only shortcut; target names and Discord IDs are never accepted as command arguments. */
public final class AccountUnlinkCommand implements CommandExecutor {
    private final DiscordSRV plugin;
    private final AccountSelfService<String> service;
    public AccountUnlinkCommand(DiscordSRV plugin) {
        this.plugin = plugin;
        this.service = new AccountSelfService<>(new AccountSelfService.Store<String>() {
            public String current(String actor) {
                return plugin.getAccountLinkManager().getDiscordIdForSelfService(UUID.fromString(actor));
            }
            public boolean remove(String actor, String expected) throws Exception {
                return plugin.getAccountLinkManager().unlinkIfMatches(expected, UUID.fromString(actor));
            }
        }, Clock.systemUTC());
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This shortcut is for players. Use /discord unlink <target> for administrator recovery.");
            return true;
        }
        if (!sender.hasPermission("discordsrv.unlink.self")) { sender.sendMessage("You cannot use this command."); return true; }
        Player player = (Player) sender;
        String actor = player.getUniqueId().toString();
        SchedulerUtil.runTaskAsynchronously(plugin, () -> {
            try {
                if (args.length == 0) {
                    String discord = service.current(actor);
                    if (discord == null) { tell(player,"You have no linked Discord account. Run /discord link to connect one."); return; }
                    String token = service.offer(actor, discord);
                    tell(player,"Unlink your Discord account? Linked-only roles or server access may be removed. Minecraft items and progress are not deleted. Confirm within 2 minutes.");
                    SchedulerUtil.runTaskForPlayer(plugin, player, () -> {
                        TextComponent confirm = new TextComponent("[Confirm unlink]");
                        confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/unlink confirm " + token));
                        TextComponent cancel = new TextComponent("  [Keep linked]");
                        cancel.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,"/unlink cancel " + token));
                        player.spigot().sendMessage(confirm, cancel);
                    });
                } else if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
                    AccountSelfService.Result result = service.confirm(actor,args[1]);
                    switch (result) {
                        case UNLINKED: tell(player,"Account unlinked. To relink, run /discord link and DM the fresh code to the bot from your preferred Discord. Contact staff if roles or access do not update correctly."); break;
                        case STALE: tell(player,"Your account link changed. Run /unlink again before confirming."); break;
                        case EXPIRED: tell(player,"Confirmation expired or was already used. Run /unlink again."); break;
                        case DENIED: tell(player,"This confirmation belongs to another player."); break;
                        default: tell(player,"Unlink outcome could not be confirmed. Check /discord linked before retrying.");
                    }
                } else if (args.length == 2 && args[0].equalsIgnoreCase("cancel")) {
                    service.cancel(actor,args[1]); tell(player,"Unlink cancelled.");
                } else tell(player,"Run /unlink to review your own account link.");
            } catch (Exception e) {
                DiscordSRV.error("Minecraft account self-service failed",e);
                tell(player,"Account service is unavailable. Check /discord linked before retrying.");
            }
        });
        return true;
    }
    private void tell(Player player,String text) {
        SchedulerUtil.runTaskForPlayer(plugin, player, () -> player.sendMessage(text));
    }
}
