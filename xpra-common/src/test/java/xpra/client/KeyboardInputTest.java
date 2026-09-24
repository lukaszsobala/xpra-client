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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KeyboardInputTest {

    @Test
    public void testKeysyms() {
        assertEquals("a", KeyboardInput.getKeysym('a'));
        assertEquals("A", KeyboardInput.getKeysym('A'));
        assertEquals("7", KeyboardInput.getKeysym('7'));
        assertEquals("space", KeyboardInput.getKeysym(' '));
        assertEquals("exclam", KeyboardInput.getKeysym('!'));
        assertEquals("asciitilde", KeyboardInput.getKeysym('~'));
        assertEquals("backslash", KeyboardInput.getKeysym('\\'));
        assertEquals("Return", KeyboardInput.getKeysym('\n'));
        assertEquals("Tab", KeyboardInput.getKeysym('\t'));
        assertEquals("U00E9", KeyboardInput.getKeysym('é'));
    }

    @Test
    public void testShift() {
        assertTrue(KeyboardInput.needsShift('A'));
        assertTrue(KeyboardInput.needsShift('!'));
        assertTrue(KeyboardInput.needsShift('"'));
        assertFalse(KeyboardInput.needsShift('a'));
        assertFalse(KeyboardInput.needsShift('1'));
        assertFalse(KeyboardInput.needsShift('\''));
        assertFalse(KeyboardInput.needsShift('é'));
    }
}
