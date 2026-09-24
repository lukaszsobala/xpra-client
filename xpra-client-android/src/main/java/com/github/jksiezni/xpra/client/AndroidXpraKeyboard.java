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

package com.github.jksiezni.xpra.client;

import android.view.KeyEvent;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import xpra.client.XpraKeyboard;

/**
 * Keys are sent by their X11 names (see {@link xpra.client.KeyboardInput}), which servers resolve
 * against a US keyboard layout, so no keycodes are sent.
 */
public class AndroidXpraKeyboard implements XpraKeyboard {

    /**
     * The X11 keysym names of the Android keys that do not produce text.
     */
    private static final SparseArray<String> SPECIAL_KEYS = new SparseArray<>();

    static {
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_DEL, "BackSpace");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_FORWARD_DEL, "Delete");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_ENTER, "Return");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_NUMPAD_ENTER, "KP_Enter");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_TAB, "Tab");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_SPACE, "space");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_ESCAPE, "Escape");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_DPAD_LEFT, "Left");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_DPAD_RIGHT, "Right");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_DPAD_UP, "Up");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_DPAD_DOWN, "Down");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_MOVE_HOME, "Home");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_MOVE_END, "End");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_PAGE_UP, "Prior");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_PAGE_DOWN, "Next");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_INSERT, "Insert");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift_L");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_SHIFT_RIGHT, "Shift_R");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_CTRL_LEFT, "Control_L");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_CTRL_RIGHT, "Control_R");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_ALT_LEFT, "Alt_L");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_ALT_RIGHT, "Alt_R");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_META_LEFT, "Super_L");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_META_RIGHT, "Super_R");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_CAPS_LOCK, "Caps_Lock");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_SYSRQ, "Print");
        SPECIAL_KEYS.put(KeyEvent.KEYCODE_BREAK, "Pause");
        for (int i = 0; i < 12; ++i) {
            SPECIAL_KEYS.put(KeyEvent.KEYCODE_F1 + i, "F" + (i + 1));
        }
    }

    /**
     * @return the X11 keysym name of an Android key that does not produce text, or null
     */
    public static String getSpecialKeysym(int keyCode) {
        return SPECIAL_KEYS.get(keyCode);
    }

    @Override
    public Locale getLocale() {
        // matches the characters to keys mapping of KeyboardInput
        return Locale.US;
    }

    @Override
    public List<KeyDesc> getKeycodes() {
        return new ArrayList<>();
    }
}
