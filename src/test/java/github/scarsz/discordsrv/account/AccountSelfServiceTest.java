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
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AccountSelfServiceTest {
    static class Time extends Clock {
        long now;
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId z) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now); }
    }
    static class Store implements AccountSelfService.Store<UUID> {
        Map<String, UUID> links = new HashMap<>(); int writes;
        public UUID current(String actor) { return links.get(actor); }
        public boolean remove(String actor, UUID expected) {
            if (!expected.equals(links.get(actor))) return false;
            writes++; links.remove(actor); return true;
        }
    }
    final Store store = new Store(); final Time time = new Time();
    final AccountSelfService<UUID> service = new AccountSelfService<>(store, time);
    final UUID old = UUID.randomUUID();
    String offer() { store.links.put("owner", old); return service.offer("owner", old); }
    @Test void confirmsOwnPairOnceAndPreservesOthers() {
        String token = offer(); store.links.put("other", UUID.randomUUID());
        assertEquals(AccountSelfService.Result.UNLINKED, service.confirm("owner", token));
        assertEquals(AccountSelfService.Result.EXPIRED, service.confirm("owner", token));
        assertEquals(1, store.writes); assertNotNull(store.links.get("other"));
    }
    @Test void attackerCannotConsumeConfirmation() {
        String token = offer();
        assertEquals(AccountSelfService.Result.DENIED, service.confirm("attacker", token));
        assertEquals(AccountSelfService.Result.UNLINKED, service.confirm("owner", token));
    }
    @Test void expiresAtDeadline() {
        String token = offer(); time.now = 120000;
        assertEquals(AccountSelfService.Result.EXPIRED, service.confirm("owner", token));
        assertEquals(old, store.links.get("owner")); assertEquals(0, store.writes);
    }
    @Test void stalePairProtectsReplacement() {
        String token = offer(); UUID next = UUID.randomUUID(); store.links.put("owner", next);
        assertEquals(AccountSelfService.Result.STALE, service.confirm("owner", token));
        assertEquals(next, store.links.get("owner")); assertEquals(0, store.writes);
    }
    @Test void cancelAndReissueRevokeOldTokens() {
        String token = offer(); service.cancel("attacker", token);
        String next = service.offer("owner", old);
        assertEquals(AccountSelfService.Result.EXPIRED, service.confirm("owner", token));
        service.cancel("owner", next);
        assertEquals(AccountSelfService.Result.EXPIRED, service.confirm("owner", next));
        assertEquals(0, store.writes);
    }
    @Test void failureIsNotSuccessAndCannotReplay() {
        AccountSelfService<UUID> broken = new AccountSelfService<>(new AccountSelfService.Store<UUID>() {
            public UUID current(String actor) { return old; }
            public boolean remove(String actor, UUID expected) throws Exception { throw new Exception("offline"); }
        }, time);
        String token = broken.offer("owner", old);
        assertEquals(AccountSelfService.Result.FAILED, broken.confirm("owner", token));
        assertEquals(AccountSelfService.Result.EXPIRED, broken.confirm("owner", token));
    }
    @Test void minecraftShortcutBindsPlayerAndOriginalDiscordPair() {
        java.util.Map<String,String> links = new java.util.HashMap<>();
        links.put("player-uuid", "discord-old");
        AccountSelfService<String> minecraft = new AccountSelfService<>(new AccountSelfService.Store<String>() {
            public String current(String actor) { return links.get(actor); }
            public boolean remove(String actor,String expected) { return links.remove(actor,expected); }
        },time);
        String token = minecraft.offer("player-uuid", minecraft.current("player-uuid"));
        assertEquals(AccountSelfService.Result.DENIED,minecraft.confirm("other-player",token));
        links.put("player-uuid", "discord-new");
        assertEquals(AccountSelfService.Result.STALE,minecraft.confirm("player-uuid",token));
        assertEquals("discord-new",links.get("player-uuid"));
        String fresh = minecraft.offer("player-uuid", minecraft.current("player-uuid"));
        assertEquals(AccountSelfService.Result.UNLINKED,minecraft.confirm("player-uuid",fresh));
    }
}
