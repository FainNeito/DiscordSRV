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
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ConditionalUnlinkTest {
    @Test void exactPairDeleteProtectsReplacementAndOtherActors() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID())) {
            c.createStatement().execute("create table accounts(discord varchar(32) unique, uuid varchar(36) unique)");
            UUID old = UUID.randomUUID(), next = UUID.randomUUID();
            try (PreparedStatement p = c.prepareStatement("insert into accounts values (?, ?)")) {
                p.setString(1,"owner"); p.setString(2,next.toString()); p.executeUpdate();
                p.setString(1,"other"); p.setString(2,old.toString()); p.executeUpdate();
            }
            assertFalse(ConditionalUnlink.jdbc(c,"accounts","owner",old)); assertEquals(2,count(c));
            assertTrue(ConditionalUnlink.jdbc(c,"accounts","owner",next));
            assertFalse(ConditionalUnlink.jdbc(c,"accounts","owner",next)); assertEquals(1,count(c));
        }
    }
    @Test void refusesForeignTransactionsAndStorageFailures() throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID())) {
            c.setAutoCommit(false);
            assertThrows(SQLException.class, () -> ConditionalUnlink.jdbc(c,"accounts","owner",UUID.randomUUID()));
            assertFalse(c.getAutoCommit()); c.rollback(); c.setAutoCommit(true);
            assertThrows(SQLException.class, () -> ConditionalUnlink.jdbc(c,"missing","owner",UUID.randomUUID()));
            assertThrows(IllegalArgumentException.class, () -> ConditionalUnlink.jdbc(c,"bad;table","owner",UUID.randomUUID()));
        }
    }
    @Test void fileFailureRetainsPairAndStaleConfirmationCannotWrite() throws Exception {
        java.util.Map<String, UUID> links = new java.util.HashMap<>();
        UUID current = UUID.randomUUID(); links.put("owner", current);
        assertThrows(java.io.IOException.class, () -> ConditionalUnlink.file(links,"owner",current,
                () -> { throw new java.io.IOException("disk full"); }));
        assertEquals(current, links.get("owner"));
        assertFalse(ConditionalUnlink.file(links,"owner",UUID.randomUUID(), () -> fail("stale persistence")));
        java.nio.file.Path file = java.nio.file.Files.createTempFile("unlink-journal", ".aof");
        try {
            assertTrue(ConditionalUnlink.file(links,"owner",current,
                    () -> java.nio.file.Files.write(file, ("-owner " + current + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            assertFalse(links.containsKey("owner"));
            assertTrue(new String(java.nio.file.Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8).contains(current.toString()));
        } finally { java.nio.file.Files.deleteIfExists(file); }
    }
    int count(Connection c) throws Exception {
        try (Statement s=c.createStatement(); ResultSet r=s.executeQuery("select count(*) from accounts")) {
            r.next(); return r.getInt(1);
        }
    }
}
