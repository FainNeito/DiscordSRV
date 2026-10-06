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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceStartupOrderTest {
    @Test void presenceThreadStartsAfterVisibilityHookRegistration() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
                "src/main/java/github/scarsz/discordsrv/DiscordSRV.java")), StandardCharsets.UTF_8);
        int start = source.indexOf("// start presence updater thread");
        int hooks = source.indexOf("// plugin hooks");
        int metadataHook = source.indexOf("pluginHooks.add(new VanishHook()");
        assertTrue(hooks >= 0 && metadataHook > hooks && start > metadataHook,
                "First presence count must not race Staff or metadata visibility hooks");
    }
}
