/*
 * Copyright (C) 2020 Jakub Ksiezniak
 *
 *     This program is free software; you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation; either version 2 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License along
 *     with this program; if not, write to the Free Software Foundation, Inc.,
 *     51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package xpra.client;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerAppTest {

    private static Map<String, Object> entry(String command, byte[] icon, String iconType) {
        Map<String, Object> props = new HashMap<>();
        props.put("command", command);
        if (icon != null) {
            props.put("IconData", icon);
            props.put("IconType", iconType);
        }
        return props;
    }

    private static Map<String, Object> category(Map<String, Object> entries) {
        Map<String, Object> props = new HashMap<>();
        props.put("Name", "category");
        props.put("IconData", new byte[]{9});
        props.put("Entries", entries);
        return props;
    }

    @Test
    public void testFromMenu() {
        Map<String, Object> development = new HashMap<>();
        development.put("Geany", entry("geany %F", new byte[]{1, 2}, "png"));
        development.put("Visual Studio Code", entry("/usr/share/code/code --unity-launch %F", null, null));
        Map<String, Object> office = new HashMap<>();
        office.put("LibreOffice", entry("libreoffice --writer %U", new byte[]{3}, "svg"));
        // the same application in two categories:
        office.put("Geany", entry("geany %F", new byte[]{1, 2}, "png"));
        Map<String, Object> menu = new HashMap<>();
        menu.put("Development", category(development));
        menu.put("Office", category(office));
        menu.put("Broken", "not a category");

        List<ServerApp> apps = ServerApp.fromMenu(menu);
        assertEquals(3, apps.size());
        assertEquals("Geany", apps.get(0).name);
        assertEquals("geany", apps.get(0).command);
        assertArrayEquals(new byte[]{1, 2}, apps.get(0).iconData);
        assertEquals("png", apps.get(0).iconType);
        assertEquals("LibreOffice", apps.get(1).name);
        assertEquals("libreoffice --writer", apps.get(1).command);
        assertEquals("Visual Studio Code", apps.get(2).name);
        assertNull(apps.get(2).iconData);
        assertEquals("code", apps.get(2).getProgramName());
    }

    @Test
    public void testFromMenuWithoutMenu() {
        assertTrue(ServerApp.fromMenu(null).isEmpty());
        assertTrue(ServerApp.fromMenu(Collections.emptyMap()).isEmpty());
    }

    @Test
    public void testCleanCommand() {
        assertEquals("geany", ServerApp.cleanCommand("geany %F"));
        assertEquals("gimp-2.10", ServerApp.cleanCommand("gimp-2.10 %U"));
        assertEquals("foo --name=bar", ServerApp.cleanCommand("foo %i --name=bar %c %k"));
        assertEquals("printf 100%", ServerApp.cleanCommand("printf 100%%"));
    }

    @Test
    public void testMatchesWindow() {
        ServerApp geany = new ServerApp("Geany", "Development", "geany", null, null, null);
        assertTrue(geany.matchesWindow(Arrays.asList("geany", "Geany")));
        assertFalse(geany.matchesWindow(Arrays.asList("xterm", "XTerm")));
        assertFalse(geany.matchesWindow(Arrays.asList("", "")));

        ServerApp code = new ServerApp("Visual Studio Code", "Development", "/usr/share/code/code --unity-launch", null, null, "Code");
        assertTrue(code.matchesWindow(Arrays.asList("code", "Code")));
        assertTrue(ServerApp.matchesWindow("libreoffice --writer", "libreoffice-writer",
            Arrays.asList("libreoffice", "libreoffice-writer")));
    }
}
