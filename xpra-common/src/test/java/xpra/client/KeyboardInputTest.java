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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xpra.protocol.packets.DrawPacket;
import xpra.protocol.packets.NewWindow;

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

    @Test
    public void testUsKeys() {
        final RecordingWindow window = new RecordingWindow();
        new KeyboardInput(window).typeText("aB");
        assertEquals(Arrays.asList("+a@38", "-a@38", "+Shift_L@50", "+B@56", "-B@56", "-Shift_L@50"), window.keys);
        assertEquals(0, window.keymaps.size());
    }

    @Test
    public void testExtraCharacters() {
        final RecordingWindow window = new RecordingWindow();
        final KeyboardInput input = new KeyboardInput(window);
        input.typeText("\u00e9\u20ac\u00e9");
        final int first = UsKeymap.SPARE_KEYCODES[0];
        final int second = UsKeymap.SPARE_KEYCODES[1];
        assertEquals(Arrays.asList("+U00E9@" + first, "-U00E9@" + first, "+U20AC@" + second, "-U20AC@" + second,
            "+U00E9@" + first, "-U00E9@" + first), window.keys);
        // a new keyboard map for each new character only:
        assertEquals(2, window.keymaps.size());
        final Map<?, ?> x11Keycodes = (Map<?, ?>) window.keymaps.get(1).get("x11_keycodes");
        assertEquals(Arrays.asList("U00E9"), x11Keycodes.get(first));
        assertEquals(Arrays.asList("U20AC"), x11Keycodes.get(second));
        assertEquals(Arrays.asList("a", "A"), x11Keycodes.get(38));
    }

    @Test
    public void testSpareKeycodesReused() {
        final RecordingWindow window = new RecordingWindow();
        final KeyboardInput input = new KeyboardInput(window);
        final int count = UsKeymap.SPARE_KEYCODES.length;
        for (int i = 0; i < count; ++i) {
            input.typeCharacter(0x4e00 + i);
        }
        // the least recently used character is replaced:
        input.typeCharacter(0x4e00);
        input.typeCharacter(0x3042);
        assertEquals("+U3042@" + UsKeymap.SPARE_KEYCODES[1], window.keys.get(window.keys.size() - 2));
        final Map<?, ?> x11Keycodes = (Map<?, ?>) window.keymaps.get(window.keymaps.size() - 1).get("x11_keycodes");
        assertEquals(Arrays.asList("U4E00"), x11Keycodes.get(UsKeymap.SPARE_KEYCODES[0]));
    }

    @Test
    public void testSpareKeycodesUnused() {
        final Map<?, ?> x11Keycodes = (Map<?, ?>) KeyboardInput.getUsKeymap().get("x11_keycodes");
        for (int keycode : UsKeymap.SPARE_KEYCODES) {
            assertFalse(x11Keycodes.containsKey(keycode));
            assertTrue(keycode > 8 && keycode < 256);
        }
    }

    @Test
    public void testLatchedModifier() {
        final RecordingWindow window = new RecordingWindow();
        final KeyboardInput input = new KeyboardInput(window);
        final int[] changes = {0};
        input.setStickyListener(() -> changes[0]++);
        input.setSticky("Control_L", KeyboardInput.Sticky.LATCHED);
        input.typeText("cd");
        // held for the next key only:
        assertEquals(Arrays.asList("+Control_L@37", "+c@54", "-c@54", "-Control_L@37", "+d@40", "-d@40"), window.keys);
        assertEquals(Arrays.asList("control"), window.modifiers.get(1));
        assertEquals(KeyboardInput.Sticky.OFF, input.getSticky("Control_L"));
        assertEquals(1, changes[0]);
    }

    @Test
    public void testLockedModifier() {
        final RecordingWindow window = new RecordingWindow();
        final KeyboardInput input = new KeyboardInput(window);
        input.setSticky("Alt_L", KeyboardInput.Sticky.LOCKED);
        input.key("Tab", true);
        input.key("Tab", false);
        input.key("Tab", true);
        input.key("Tab", false);
        assertEquals(KeyboardInput.Sticky.LOCKED, input.getSticky("Alt_L"));
        input.setSticky("Alt_L", KeyboardInput.Sticky.OFF);
        assertEquals(Arrays.asList("+Alt_L@64", "+Tab@23", "-Tab@23", "+Tab@23", "-Tab@23", "-Alt_L@64"), window.keys);
    }

    private static class RecordingWindow extends XpraWindow {

        final List<String> keys = new ArrayList<>();
        final List<List<String>> modifiers = new ArrayList<>();
        final List<Map<String, Object>> keymaps = new ArrayList<>();

        RecordingWindow() {
            super(newWindow());
        }

        private static NewWindow newWindow() {
            final NewWindow packet = new NewWindow() {
                {
                    deserialize(Arrays.<Object>asList(1, 0, 0, 100, 100, new HashMap<String, Object>()).iterator());
                }
            };
            return packet;
        }

        @Override
        public void keyboardAction(int keycode, String keyname, boolean pressed, List<String> modifiers, String string) {
            keys.add((pressed ? "+" : "-") + keyname + "@" + keycode);
            this.modifiers.add(new ArrayList<>(modifiers));
        }

        @Override
        public void keymapChanged(Map<String, Object> keymap) {
            keymaps.add(keymap);
        }

        @Override
        public void onDraw(DrawPacket packet) {
        }
    }
}
