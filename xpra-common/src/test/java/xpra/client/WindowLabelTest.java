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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WindowLabelTest {

    private static final List<ServerApp> APPS = Collections.singletonList(
        new ServerApp("Visual Studio Code", "Development", "/usr/share/code/code --unity-launch", null, null, "Code"));

    @Test
    public void keepsARealTitle() {
        assertEquals("notes.txt - Geany",
            XpraWindow.label("notes.txt - Geany", Arrays.asList("geany", "Geany"), "geany", APPS));
        assertEquals("notes.txt", XpraWindow.label(" notes.txt ", Collections.emptyList(), null, APPS));
    }

    @Test
    public void namesTheAppOfAScriptTitle() {
        assertEquals("Visual Studio Code",
            XpraWindow.label("app.js", Arrays.asList("code", "Code"), null, APPS));
    }

    @Test
    public void usesTheWindowClassOfAnUnknownApp() {
        assertEquals("Myapp", XpraWindow.label("app.js", Arrays.asList("myapp", "myapp"), null, APPS));
        assertEquals("Foo", XpraWindow.label(null, Arrays.asList("foo", "Foo"), null, APPS));
        assertEquals("Foo", XpraWindow.label("/opt/foo/bin/foo", Arrays.asList("", "Foo"), null, APPS));
    }

    @Test
    public void replacesTheNameOfTheProgram() {
        assertEquals("Visual Studio Code",
            XpraWindow.label("code", Arrays.asList("code", "Code"), "/usr/share/code/code", APPS));
    }

    @Test
    public void keepsATechnicalTitleWhenNothingIsBetter() {
        assertEquals("app.js", XpraWindow.label("app.js", Collections.emptyList(), null, APPS));
        assertNull(XpraWindow.label("  ", Collections.emptyList(), null, APPS));
    }
}
