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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Types text into a window by sending the X11 key names (keysyms) of each character.
 * <p>
 * Servers resolve key names against their keyboard layout, which clients using this class
 * should set to "us". Characters that need the shift key on that layout are wrapped in
 * Shift_L presses, as servers older than v7 do not add it automatically.
 * <p>
 * Every key event carries the modifiers currently held down, as servers set their modifier
 * state from it: without "shift" in that list, a server releases the shift key.
 */
public class KeyboardInput {

    public static final String SHIFT = "Shift_L";

    /**
     * X11 keysym names of the printable ASCII characters that are not letters or digits.
     */
    private static final Map<Character, String> KEYSYMS = new HashMap<>();

    /**
     * The characters typed with the shift key on a US keyboard.
     */
    private static final String SHIFTED = "~!@#$%^&*()_+{}|:\"<>?";

    static {
        final String chars = " !\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";
        final String[] names = {"space", "exclam", "quotedbl", "numbersign", "dollar", "percent", "ampersand",
            "apostrophe", "parenleft", "parenright", "asterisk", "plus", "comma", "minus", "period", "slash",
            "colon", "semicolon", "less", "equal", "greater", "question", "at", "bracketleft", "backslash",
            "bracketright", "asciicircum", "underscore", "grave", "braceleft", "bar", "braceright", "asciitilde"};
        for (int i = 0; i < chars.length(); ++i) {
            KEYSYMS.put(chars.charAt(i), names[i]);
        }
    }

    /**
     * The X11 modifier of each modifier key.
     */
    private static final Map<String, String> MODIFIERS = new HashMap<>();

    static {
        MODIFIERS.put("Shift_L", "shift");
        MODIFIERS.put("Shift_R", "shift");
        MODIFIERS.put("Control_L", "control");
        MODIFIERS.put("Control_R", "control");
        MODIFIERS.put("Alt_L", "mod1");
        MODIFIERS.put("Alt_R", "mod1");
        MODIFIERS.put("Super_L", "mod4");
        MODIFIERS.put("Super_R", "mod4");
    }

    private final XpraWindow window;

    /**
     * The modifier keys currently held down.
     */
    private final Set<String> pressedModifierKeys = new LinkedHashSet<>();

    /**
     * A distinct client keycode for each key name: servers find the key to release by its
     * client keycode, so using the same one for all keys would release the wrong keys.
     */
    private final Map<String, Integer> keycodes = new HashMap<>();

    public KeyboardInput(XpraWindow window) {
        this.window = window;
    }

    /**
     * Types the given text, ie: as committed by an on-screen keyboard.
     */
    public void typeText(CharSequence text) {
        for (int i = 0; i < text.length(); ) {
            final int codepoint = Character.codePointAt(text, i);
            i += Character.charCount(codepoint);
            typeCharacter(codepoint);
        }
    }

    public void typeCharacter(int codepoint) {
        final String keysym = getKeysym(codepoint);
        final boolean shift = needsShift(codepoint);
        if (shift) {
            key(SHIFT, true);
        }
        key(keysym, true);
        key(keysym, false);
        if (shift) {
            key(SHIFT, false);
        }
    }

    /**
     * Presses or releases a key, given its X11 keysym name, ie: "Return" or "Control_L".
     */
    public void key(String keysym, boolean pressed) {
        if (MODIFIERS.containsKey(keysym)) {
            if (pressed) {
                pressedModifierKeys.add(keysym);
            } else {
                pressedModifierKeys.remove(keysym);
            }
        }
        window.keyboardAction(getKeycode(keysym), keysym, pressed, getModifiers(), null);
    }

    private int getKeycode(String keysym) {
        Integer keycode = keycodes.get(keysym);
        if (keycode == null) {
            keycode = keycodes.size() + 1;
            keycodes.put(keysym, keycode);
        }
        return keycode;
    }

    /**
     * @return the X11 modifiers currently held down, ie: "shift" or "control"
     */
    public java.util.List<String> getModifiers() {
        final Set<String> modifiers = new LinkedHashSet<>();
        for (String key : pressedModifierKeys) {
            modifiers.add(MODIFIERS.get(key));
        }
        return new ArrayList<>(modifiers);
    }

    /**
     * Releases all the modifier keys still held down, ie: when the keyboard is hidden.
     */
    public void releaseModifiers() {
        for (String key : new ArrayList<>(pressedModifierKeys)) {
            key(key, false);
        }
    }

    /**
     * @return the X11 keysym name of a character
     */
    public static String getKeysym(int codepoint) {
        if (codepoint == '\n' || codepoint == '\r') {
            return "Return";
        }
        if (codepoint == '\t') {
            return "Tab";
        }
        if ((codepoint >= 'a' && codepoint <= 'z') || (codepoint >= 'A' && codepoint <= 'Z')
            || (codepoint >= '0' && codepoint <= '9')) {
            return String.valueOf((char) codepoint);
        }
        final String name = codepoint < 0x80 ? KEYSYMS.get((char) codepoint) : null;
        if (name != null) {
            return name;
        }
        // other Unicode characters: only typed if the server's layout has them
        return String.format("U%04X", codepoint);
    }

    /**
     * @return true if the character is typed with the shift key on a US keyboard
     */
    public static boolean needsShift(int codepoint) {
        return (codepoint >= 'A' && codepoint <= 'Z') || (codepoint < 0x80 && SHIFTED.indexOf(codepoint) >= 0);
    }
}
