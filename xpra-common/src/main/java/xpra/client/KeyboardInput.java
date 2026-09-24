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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 * <p>
 * Other characters, ie: "é" or "€", are not on a US keyboard, and servers ignore the keys
 * they cannot find in their keyboard map. These are assigned to keycodes that the US layout
 * leaves unused, and sent to the server in a new keyboard map before they are typed.
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

    /**
     * The modifier of each modifier key, which servers need to set their modifier state.
     */
    public static final Map<String, Object> MOD_MEANINGS;

    static {
        final Map<String, Object> meanings = new LinkedHashMap<>();
        meanings.put("Shift_L", "shift");
        meanings.put("Shift_R", "shift");
        meanings.put("Caps_Lock", "lock");
        meanings.put("Control_L", "control");
        meanings.put("Control_R", "control");
        meanings.put("Alt_L", "mod1");
        meanings.put("Alt_R", "mod1");
        meanings.put("Meta_L", "mod1");
        meanings.put("Num_Lock", "mod2");
        meanings.put("Super_L", "mod4");
        meanings.put("Super_R", "mod4");
        meanings.put("ISO_Level3_Shift", "mod5");
        MOD_MEANINGS = Collections.unmodifiableMap(meanings);
    }

    /**
     * The keycode of each keysym on the US layout.
     */
    private static final Map<String, Integer> US_KEYCODES = new HashMap<>();

    static {
        for (String key : UsKeymap.KEYS) {
            final String[] fields = key.split(" ");
            for (int i = 1; i < fields.length; ++i) {
                US_KEYCODES.putIfAbsent(fields[i], Integer.valueOf(fields[0]));
            }
        }
    }

    /**
     * The client keycodes of the keys missing from the US layout start above the X11 keycodes.
     */
    private static final int FIRST_KEYCODE = 1000;

    private final XpraWindow window;

    /**
     * The characters that are not on a US keyboard, by keysym name, and the spare keycodes
     * they are mapped to, the least recently used first.
     */
    private final LinkedHashMap<String, Integer> extraKeys = new LinkedHashMap<>(16, 0.75f, true);

    /**
     * The modifier keys currently held down.
     */
    private final Set<String> pressedModifierKeys = new LinkedHashSet<>();

    /**
     * A distinct client keycode for each key name missing from the US layout: servers find the
     * key to release by its client keycode, so using the same one for all keys would release
     * the wrong keys.
     */
    private final Map<String, Integer> keycodes = new HashMap<>();

    /**
     * The state of a sticky modifier key, ie: the Ctrl key of an on-screen key row.
     */
    public enum Sticky {
        /** not held down */
        OFF,
        /** held down for the next key only */
        LATCHED,
        /** held down until turned off */
        LOCKED
    }

    /**
     * Notified when a sticky modifier is released after the key it applied to.
     */
    public interface StickyListener {
        void onStickyChanged();
    }

    private final Map<String, Sticky> sticky = new LinkedHashMap<>();
    private StickyListener stickyListener;

    public KeyboardInput(XpraWindow window) {
        this.window = window;
    }

    public void setStickyListener(StickyListener listener) {
        this.stickyListener = listener;
    }

    public Sticky getSticky(String keysym) {
        final Sticky state = sticky.get(keysym);
        return state != null ? state : Sticky.OFF;
    }

    /**
     * Holds a modifier key down, for the next key or until turned off, or releases it.
     */
    public void setSticky(String keysym, Sticky state) {
        final Sticky previous = getSticky(keysym);
        if (previous == state) {
            return;
        }
        if (state == Sticky.OFF) {
            sticky.remove(keysym);
            key(keysym, false);
        } else {
            sticky.put(keysym, state);
            if (previous == Sticky.OFF) {
                key(keysym, true);
            }
        }
    }

    /**
     * Releases the modifiers latched for the key just typed.
     */
    private void releaseLatched() {
        boolean changed = false;
        for (Map.Entry<String, Sticky> e : new ArrayList<>(sticky.entrySet())) {
            if (e.getValue() == Sticky.LATCHED) {
                sticky.remove(e.getKey());
                key(e.getKey(), false);
                changed = true;
            }
        }
        if (changed && stickyListener != null) {
            stickyListener.onStickyChanged();
        }
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
        if (!isOnUsKeyboard(codepoint)) {
            if (!Character.isISOControl(codepoint)) {
                typeExtraCharacter(getKeysym(codepoint));
            }
            return;
        }
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
        releaseLatched();
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
        if (!pressed && !MODIFIERS.containsKey(keysym)) {
            releaseLatched();
        }
    }

    private void typeExtraCharacter(String keysym) {
        Integer keycode = extraKeys.get(keysym);
        if (keycode == null) {
            if (extraKeys.size() < UsKeymap.SPARE_KEYCODES.length) {
                keycode = UsKeymap.SPARE_KEYCODES[extraKeys.size()];
            } else {
                // replace the least recently used character:
                final Iterator<Integer> eldest = extraKeys.values().iterator();
                keycode = eldest.next();
                eldest.remove();
            }
            extraKeys.put(keysym, keycode);
            window.keymapChanged(getKeymap());
        }
        window.keyboardAction(keycode, keysym, true, getModifiers(), null);
        window.keyboardAction(keycode, keysym, false, getModifiers(), null);
        releaseLatched();
    }

    /**
     * @return a keyboard map with the US layout and the extra characters on the spare keycodes:
     * servers replace all the keys between the lowest and highest keycodes of a map, so it must
     * include every key
     */
    Map<String, Object> getKeymap() {
        return buildKeymap(extraKeys);
    }

    /**
     * @return a keyboard map with only the US layout, for clients typing with this class
     */
    public static Map<String, Object> getUsKeymap() {
        return buildKeymap(Collections.<String, Integer>emptyMap());
    }

    private static Map<String, Object> buildKeymap(Map<String, Integer> extraKeys) {
        final List<Object> keycodes = new ArrayList<>();
        final Map<Object, Object> x11Keycodes = new LinkedHashMap<>();
        for (String key : UsKeymap.KEYS) {
            final String[] fields = key.split(" ");
            final Integer keycode = Integer.valueOf(fields[0]);
            final List<String> keysyms = new ArrayList<>();
            for (int level = 1; level < fields.length; ++level) {
                final String keysym = "-".equals(fields[level]) ? "" : fields[level];
                keysyms.add(keysym);
                if (!keysym.isEmpty()) {
                    // (keyval, keyname, keycode, group, level):
                    keycodes.add(Arrays.asList(0, keysym, keycode, 0, level - 1));
                }
            }
            x11Keycodes.put(keycode, keysyms);
        }
        for (Map.Entry<String, Integer> e : extraKeys.entrySet()) {
            keycodes.add(Arrays.asList(0, e.getKey(), e.getValue(), 0, 0));
            x11Keycodes.put(e.getValue(), Collections.singletonList(e.getKey()));
        }
        final Map<String, Object> query = new LinkedHashMap<>();
        query.put("rules", "evdev");
        query.put("model", "pc105");
        query.put("layout", "us");
        final Map<String, Object> keymap = new LinkedHashMap<>();
        keymap.put("layout", "us");
        keymap.put("keycodes", keycodes);
        keymap.put("x11_keycodes", x11Keycodes);
        keymap.put("query_struct", query);
        keymap.put("mod_meanings", MOD_MEANINGS);
        return keymap;
    }

    private int getKeycode(String keysym) {
        final Integer usKeycode = US_KEYCODES.get(keysym);
        if (usKeycode != null) {
            return usKeycode;
        }
        Integer keycode = keycodes.get(keysym);
        if (keycode == null) {
            keycode = FIRST_KEYCODE + keycodes.size();
            keycodes.put(keysym, keycode);
        }
        return keycode;
    }

    /**
     * @return the X11 modifiers currently held down, ie: "shift" or "control"
     */
    public List<String> getModifiers() {
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
        sticky.clear();
        for (String key : new ArrayList<>(pressedModifierKeys)) {
            key(key, false);
        }
        if (stickyListener != null) {
            stickyListener.onStickyChanged();
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
        return String.format("U%04X", codepoint);
    }

    /**
     * @return true if the character has a key on a US keyboard
     */
    public static boolean isOnUsKeyboard(int codepoint) {
        return (codepoint >= 0x20 && codepoint < 0x7f) || codepoint == '\n' || codepoint == '\r' || codepoint == '\t';
    }

    /**
     * @return true if the character is typed with the shift key on a US keyboard
     */
    public static boolean needsShift(int codepoint) {
        return (codepoint >= 'A' && codepoint <= 'Z') || (codepoint < 0x80 && SHIFTED.indexOf(codepoint) >= 0);
    }
}
