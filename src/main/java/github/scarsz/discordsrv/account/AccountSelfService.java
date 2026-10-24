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

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Framework-independent confirmation policy. No actor or Minecraft target comes from client input. */
public final class AccountSelfService {
    public interface Store {
        UUID current(String actor);
        boolean remove(String actor, UUID expected) throws Exception;
    }
    public enum Result { UNLINKED, STALE, EXPIRED, DENIED, FAILED }
    private static final long TTL = 120_000;
    private final Store store;
    private final Clock clock;
    private final Map<String, Pending> pending = new HashMap<>();
    public AccountSelfService(Store store, Clock clock) { this.store = store; this.clock = clock; }
    public UUID current(String actor) { return store.current(actor); }
    public synchronized String offer(String actor, UUID expected) {
        long now = clock.millis();
        pending.values().removeIf(p -> p.deadline <= now || p.actor.equals(actor));
        if (pending.size() >= 4096) throw new IllegalStateException("Account confirmation capacity reached");
        String token = UUID.randomUUID().toString();
        pending.put(token, new Pending(actor, expected, now + TTL));
        return token;
    }
    public synchronized Result confirm(String actor, String token) {
        Pending p = pending.get(token);
        if (p == null) return Result.EXPIRED;
        if (!p.actor.equals(actor)) return Result.DENIED;
        pending.remove(token);
        if (clock.millis() >= p.deadline) return Result.EXPIRED;
        try { return store.remove(actor, p.expected) ? Result.UNLINKED : Result.STALE; }
        catch (Exception e) { return Result.FAILED; }
    }
    public synchronized void cancel(String actor, String token) {
        Pending p = pending.get(token);
        if (p != null && p.actor.equals(actor)) pending.remove(token);
    }
    private static final class Pending {
        final String actor;
        final UUID expected;
        final long deadline;
        Pending(String actor, UUID expected, long deadline) {
            this.actor = actor; this.expected = expected; this.deadline = deadline;
        }
    }
}
