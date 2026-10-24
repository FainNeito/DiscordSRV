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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

public final class ConditionalUnlink {
    private ConditionalUnlink() { }

    public static boolean jdbc(Connection connection, String table, String actor, UUID expected) throws SQLException {
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid account table");
        // Never commit another caller's transaction or report an uncommitted deletion.
        if (!connection.getAutoCommit()) throw new SQLException("Conditional unlink requires auto-commit");
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from " + table + " where discord = ? and uuid = ?")) {
            statement.setString(1, actor);
            statement.setString(2, expected.toString());
            return statement.executeUpdate() == 1;
        }
    }
    @FunctionalInterface public interface Persist { void remove() throws java.io.IOException; }
    public static boolean file(java.util.Map<String, UUID> links, String actor, UUID expected, Persist persist) throws java.io.IOException {
        synchronized (links) {
            if (!expected.equals(links.get(actor))) return false;
            persist.remove();
            links.remove(actor);
            return true;
        }
    }
}
