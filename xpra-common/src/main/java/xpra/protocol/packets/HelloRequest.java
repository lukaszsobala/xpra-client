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

package xpra.protocol.packets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import xpra.client.KeyboardInput;
import xpra.client.XpraKeyboard;
import xpra.client.XpraKeyboard.KeyDesc;
import xpra.protocol.PictureEncoding;
import xpra.protocol.ProtocolConstants;

/**
 * The client "hello" packet. The capabilities follow the layout used by Xpra v5+ clients:
 * most settings are nested in dictionaries ("encoding", "keymap", "display"...).
 */
public class HelloRequest extends xpra.protocol.IOPacket {

    private static final int[] CAPS_MAX_ICON_SIZE = {128, 128};

    private final Map<String, Object> caps = new LinkedHashMap<>();
    private final Map<String, Object> display = new LinkedHashMap<>();

    public HelloRequest(int screenWidth, int screenHeight, XpraKeyboard keyboard, PictureEncoding defaultEncoding, PictureEncoding[] encodings) {
        super("hello");
        caps.put("version", ProtocolConstants.VERSION);
        caps.put("protocol-version", ProtocolConstants.MIN_SERVER_VERSION);
        caps.put("client_type", "Java");
        caps.put("platform", System.getProperty("os.name").toLowerCase(Locale.ROOT));
        caps.put("uuid", UUID.randomUUID().toString().replace("-", ""));
        final String localUser = System.getProperty("user.name", "");
        setUsername(localUser.isEmpty() ? "xpra" : localUser);

        // packet encoding and compression:
        caps.put("encoders", Arrays.asList("rencodeplus"));
        caps.put("rencodeplus", true);
        caps.put("compressors", Arrays.asList("lz4", "none"));
        caps.put("lz4", true);

        // this client does not support any of these features:
        caps.put("clipboard", false);
        caps.put("notifications", false);
        caps.put("cursors", false);
        caps.put("bell", false);
        caps.put("audio", false);
        caps.put("file-transfer", false);
        caps.put("printing", false);
        caps.put("webcam", false);
        caps.put("mmap", false);
        caps.put("share", false);
        caps.put("ping", true);

        // the applications of the server, to start them from this client:
        caps.put("menu", true);
        // servers older than 6.4:
        caps.put("xdg-menu", true);

        // it is required, if client wants to display windows (since 4.x)
        caps.put("windows", true);

        // without it, servers silently ignore all pointer events:
        final Map<String, Object> doubleClick = new LinkedHashMap<>();
        doubleClick.put("time", -1);
        doubleClick.put("distance", Arrays.asList(-1, -1));
        final Map<String, Object> pointer = new LinkedHashMap<>();
        pointer.put("double_click", doubleClick);
        caps.put("pointer", pointer);

        // "core" lists what we can decode, "options" what users can choose, where "rgb" means rgb24 or rgb32:
        final List<String> coreEncodings = Arrays.asList(PictureEncoding.toString(encodings));
        final List<String> options = new ArrayList<>();
        for (String name : coreEncodings) {
            final String option = toOption(name);
            if (!options.contains(option)) {
                options.add(option);
            }
        }
        final Map<String, Object> encoding = new LinkedHashMap<>();
        encoding.put("options", options);
        encoding.put("core", coreEncodings);
        encoding.put("window-icon", Arrays.asList(PictureEncoding.png.toString()));
        encoding.put("rgb_formats", Arrays.asList("RGB", "RGBX", "RGBA"));
        encoding.put("rgb_lz4", true);
        encoding.put("transparency", false);
        encoding.put("icons", Collections.singletonMap("max_size", CAPS_MAX_ICON_SIZE));
        if (coreEncodings.contains(PictureEncoding.jpeg.toString())) {
            // the input formats our jpeg decoder handles:
            encoding.put("full_csc_modes", Collections.singletonMap(PictureEncoding.jpeg.toString(),
                Arrays.asList("BGRX", "BGRA", "YUV420P")));
        }
        if (defaultEncoding != null) {
            encoding.put("setting", toOption(defaultEncoding.toString()));
        }
        caps.put("encoding", encoding);

        final int[] screenDims = new int[]{screenWidth, screenHeight};
        caps.put("desktop_size", screenDims);
        display.put("desktop_size", screenDims);
        setDpi(96, 0, 0);
        caps.put("display", display);

        setKeyboard(keyboard);
    }

    /**
     * Enables sharing the clipboard, see {@link xpra.client.ClipboardSync}.
     */
    public void setClipboard(Map<String, Object> clipboardCaps) {
        caps.put("clipboard", clipboardCaps);
    }

    /**
     * The name of the user on the client side, which servers require even without authentication.
     */
    public void setUsername(String username) {
        caps.put("username", username);
    }

    public void setDpi(int dpi, int xdpi, int ydpi) {
        final Map<String, Object> dpiMap = new LinkedHashMap<>();
        dpiMap.put("", dpi);
        dpiMap.put("x", xdpi > 0 ? xdpi : dpi);
        dpiMap.put("y", ydpi > 0 ? ydpi : dpi);
        caps.put("dpi", dpiMap);
        display.put("dpi", dpiMap);
    }

    private void setKeyboard(XpraKeyboard keyboard) {
        caps.put("keyboard", keyboard != null);
        caps.put("keyboard_sync", false);
        if (keyboard == null) {
            return;
        }
        final Map<String, Object> keymap = new LinkedHashMap<>();
        keymap.put("layout", getLayout(keyboard.getLocale()));
        final String variant = keyboard.getLocale().getVariant();
        if (!variant.isEmpty()) {
            keymap.put("variant", variant);
        }
        final List<KeyDesc> keycodes = keyboard.getKeycodes();
        if (keycodes.isEmpty()) {
            // keys are sent by name, on a known keyboard map which replaces the server's:
            keymap.putAll(KeyboardInput.getUsKeymap());
        } else {
            keymap.put("keycodes", buildKeycodes(keycodes));
        }
        // the modifier of each modifier key, which servers need to set the modifier state:
        keymap.put("mod_meanings", KeyboardInput.MOD_MEANINGS);
        keymap.put("sync", false);
        caps.put("keymap", keymap);
    }

    private static String toOption(String encoding) {
        if (PictureEncoding.rgb24.toString().equals(encoding) || PictureEncoding.rgb32.toString().equals(encoding)) {
            return "rgb";
        }
        return encoding;
    }

    /**
     * @return the X11 keyboard layout name for the given locale
     */
    private static String getLayout(Locale locale) {
        final String language = locale.getLanguage();
        if (language.isEmpty() || "en".equals(language)) {
            return "us";
        }
        return language;
    }

    private Object buildKeycodes(List<KeyDesc> keycodes) {
        List<Object> list = new ArrayList<>();
        for (KeyDesc kd : keycodes) {
            list.add(kd.toList());
        }
        return list;
    }

    @Override
    protected void serialize(Collection<Object> elems) {
        elems.add(caps);
    }

}
