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

/**
 * The X11 keyboard map of the "us" layout with the "evdev" rules and the "pc105" model,
 * without the vendor specific keys (XF86*), as set by: setxkbmap -rules evdev -model pc105 -layout us
 */
final class UsKeymap {

    /**
     * Each entry is a keycode followed by its keysyms without and with shift, "-" when there is none.
     */
    static final String[] KEYS = {
        "9 Escape", "10 1 exclam", "11 2 at", "12 3 numbersign", "13 4 dollar", "14 5 percent",
        "15 6 asciicircum", "16 7 ampersand", "17 8 asterisk", "18 9 parenleft", "19 0 parenright",
        "20 minus underscore", "21 equal plus", "22 BackSpace BackSpace", "23 Tab ISO_Left_Tab", "24 q Q",
        "25 w W", "26 e E", "27 r R", "28 t T", "29 y Y", "30 u U", "31 i I", "32 o O", "33 p P",
        "34 bracketleft braceleft", "35 bracketright braceright", "36 Return", "37 Control_L", "38 a A",
        "39 s S", "40 d D", "41 f F", "42 g G", "43 h H", "44 j J", "45 k K", "46 l L", "47 semicolon colon",
        "48 apostrophe quotedbl", "49 grave asciitilde", "50 Shift_L", "51 backslash bar", "52 z Z", "53 x X",
        "54 c C", "55 v V", "56 b B", "57 n N", "58 m M", "59 comma less", "60 period greater",
        "61 slash question", "62 Shift_R", "63 KP_Multiply KP_Multiply", "64 Alt_L Meta_L", "65 space",
        "66 Caps_Lock", "67 F1 F1", "68 F2 F2", "69 F3 F3", "70 F4 F4", "71 F5 F5", "72 F6 F6", "73 F7 F7",
        "74 F8 F8", "75 F9 F9", "76 F10 F10", "77 Num_Lock", "78 Scroll_Lock", "79 KP_Home KP_7",
        "80 KP_Up KP_8", "81 KP_Prior KP_9", "82 KP_Subtract KP_Subtract", "83 KP_Left KP_4",
        "84 KP_Begin KP_5", "85 KP_Right KP_6", "86 KP_Add KP_Add", "87 KP_End KP_1", "88 KP_Down KP_2",
        "89 KP_Next KP_3", "90 KP_Insert KP_0", "91 KP_Delete KP_Decimal", "92 ISO_Level3_Shift",
        "94 less greater", "95 F11 F11", "96 F12 F12", "98 Katakana", "99 Hiragana", "100 Henkan_Mode",
        "101 Hiragana_Katakana", "102 Muhenkan", "104 KP_Enter", "105 Control_R", "106 KP_Divide KP_Divide",
        "107 Print Sys_Req", "108 Alt_R Meta_R", "109 Linefeed", "110 Home", "111 Up", "112 Prior",
        "113 Left", "114 Right", "115 End", "116 Down", "117 Next", "118 Insert", "119 Delete",
        "125 KP_Equal", "126 plusminus", "127 Pause Break", "129 KP_Decimal KP_Decimal", "130 Hangul",
        "131 Hangul_Hanja", "133 Super_L", "134 Super_R", "135 Menu", "136 Cancel", "137 Redo",
        "138 SunProps", "139 Undo", "140 SunFront", "144 Find", "146 Help", "187 parenleft", "188 parenright",
        "190 Redo", "203 ISO_Level5_Shift", "204 - Alt_L", "205 - Meta_L", "206 - Super_L", "207 - Hyper_L",
        "218 Print", "231 Cancel"
    };

    /**
     * The keycodes with no key in {@link #KEYS}.
     */
    static final int[] SPARE_KEYCODES = {
        93, 97, 103, 120, 121, 122, 123, 124, 128, 132, 141, 142, 143, 145, 147, 148, 149, 150, 151, 152, 153,
        154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172, 173,
        174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 189, 191, 192, 193, 194, 195, 196,
        197, 198, 199, 200, 201, 202, 208, 209, 210, 211, 212, 213, 214, 215, 216, 217, 219, 220, 221, 222,
        223, 224, 225, 226, 227, 228, 229, 230, 232, 233, 234, 235, 236, 237, 238, 239, 240, 241, 242, 243,
        244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 254, 255
    };

    private UsKeymap() {
    }
}
