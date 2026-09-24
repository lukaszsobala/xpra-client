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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xpra.protocol.XpraSender;
import xpra.protocol.packets.ClipboardPacket;

/**
 * Shares the text of the clipboard with the server, in both directions.
 * <p>
 * This client is a "greedy" clipboard peer: the server sends the copied text along with its
 * clipboard token, and this client does the same, so that a copy on one side is available on
 * the other straight away, without waiting for a paste to request it.
 */
public class ClipboardSync {

    private static final Logger logger = LoggerFactory.getLogger(ClipboardSync.class);

    /**
     * The local clipboard, ie: Android's.
     */
    public interface LocalClipboard {
        /**
         * Called when text was copied on the server. It may be called from any thread.
         */
        void setText(String text);
    }

    public static final String SELECTION = "CLIPBOARD";
    public static final String UTF8_STRING = "UTF8_STRING";

    /**
     * The text targets this client provides and understands, the preferred one first.
     */
    public static final List<String> TEXT_TARGETS = Collections.unmodifiableList(Arrays.asList(
        UTF8_STRING, "text/plain;charset=utf-8", "STRING", "TEXT", "text/plain"));

    private final LocalClipboard local;
    private XpraSender sender;

    /** the text both sides have, to not send back what was just received */
    private volatile String text;
    private long requestId;

    public ClipboardSync(LocalClipboard local) {
        this.local = local;
    }

    void setSender(XpraSender sender) {
        this.sender = sender;
    }

    /**
     * @return the "clipboard" capabilities of the hello packet
     */
    public static Map<String, Object> getCaps() {
        final Map<String, Object> caps = new LinkedHashMap<>();
        caps.put("", true);
        caps.put("enabled", true);
        caps.put("notifications", false);
        caps.put("greedy", true);
        caps.put("want_targets", true);
        caps.put("selections", Collections.singletonList(SELECTION));
        caps.put("preferred-targets", TEXT_TARGETS);
        return caps;
    }

    /**
     * The local clipboard now has this text, ie: it was copied on the device.
     */
    public void onLocalText(String newText) {
        if (newText == null || newText.equals(text)) {
            return;
        }
        text = newText;
        final XpraSender s = sender;
        if (s == null) {
            return;
        }
        // the targets, the contents of one of them, then: claim the selection, greedy, not synchronous
        s.send(new ClipboardPacket("clipboard-token", SELECTION, TEXT_TARGETS,
            UTF8_STRING, UTF8_STRING, 8, "bytes", newText.getBytes(StandardCharsets.UTF_8), true, true, false));
    }

    void process(ClipboardPacket packet) {
        logger.debug("process(" + packet + ")");
        switch (packet.type) {
            case "clipboard-token":
                processToken(packet);
                break;
            case "clipboard-contents":
                processContents(packet);
                break;
            case "clipboard-request":
                processRequest(packet);
                break;
            default:
                // "clipboard-contents-none", "clipboard-pending-requests"...
                break;
        }
    }

    /**
     * ["clipboard-token", selection, targets, target, dtype, dformat, wire-encoding, data, claim, greedy]:
     * something was copied on the server.
     */
    private void processToken(ClipboardPacket packet) {
        if (!SELECTION.equals(packet.getString(0))) {
            return;
        }
        if (packet.size() >= 7 && TEXT_TARGETS.contains(packet.getString(2)) && "bytes".equals(packet.getString(5))) {
            received(packet.getBytes(6));
            return;
        }
        // no contents with the token: ask for them
        for (String target : TEXT_TARGETS) {
            if (packet.getStrings(1).contains(target)) {
                sender.send(new ClipboardPacket("clipboard-request", ++requestId, SELECTION, target));
                return;
            }
        }
    }

    /**
     * ["clipboard-contents", request-id, selection, dtype, dformat, wire-encoding, data, truncated]
     */
    private void processContents(ClipboardPacket packet) {
        if (SELECTION.equals(packet.getString(1)) && "bytes".equals(packet.getString(4))) {
            received(packet.getBytes(5));
        }
    }

    /**
     * ["clipboard-request", request-id, selection, target]: an application on the server pastes
     * the text that was copied on the device.
     */
    private void processRequest(ClipboardPacket packet) {
        final Object id = packet.get(0);
        final String selection = packet.getString(1);
        final String target = packet.getString(2);
        final String current = text;
        if (current != null && SELECTION.equals(selection)) {
            if ("TARGETS".equals(target)) {
                sender.send(new ClipboardPacket("clipboard-contents", id, selection, "ATOM", 32, "atoms", TEXT_TARGETS, 0));
                return;
            }
            if (TEXT_TARGETS.contains(target)) {
                sender.send(new ClipboardPacket("clipboard-contents", id, selection, UTF8_STRING, 8, "bytes",
                    current.getBytes(StandardCharsets.UTF_8), 0));
                return;
            }
        }
        sender.send(new ClipboardPacket("clipboard-contents-none", id, selection));
    }

    private void received(byte[] data) {
        if (data == null) {
            return;
        }
        final String newText = new String(data, StandardCharsets.UTF_8);
        if (!newText.equals(text)) {
            text = newText;
            local.setText(newText);
        }
    }
}
