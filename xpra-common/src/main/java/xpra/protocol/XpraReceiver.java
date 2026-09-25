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

package xpra.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import xpra.protocol.packets.ConfigureWindowOverrideRedirect;
import xpra.protocol.packets.ClipboardPacket;
import xpra.protocol.packets.CursorPacket;
import xpra.protocol.packets.Disconnect;
import xpra.protocol.packets.DrawPacket;
import xpra.protocol.packets.HelloResponse;
import xpra.protocol.packets.LostWindow;
import xpra.protocol.packets.NewWindow;
import xpra.protocol.packets.NewWindowOverrideRedirect;
import xpra.protocol.packets.Ping;
import xpra.protocol.packets.RaiseWindow;
import xpra.protocol.packets.SettingChange;
import xpra.protocol.packets.StartupComplete;
import xpra.protocol.packets.WindowIcon;
import xpra.protocol.packets.WindowMetadata;

/**
 *
 */
public class XpraReceiver {
    private static final Logger logger = LoggerFactory.getLogger(XpraReceiver.class);
    private static final Map<String, Builder<Packet>> PACKETS_MAP = new HashMap<>();

    private final Map<Class<?>, PacketHandler<?>> handlers = new HashMap<>();

    static {
        PACKETS_MAP.put("hello", HelloResponse::new);
        PACKETS_MAP.put("cursor", CursorPacket::new);
        PACKETS_MAP.put("ping", Ping::new);
        PACKETS_MAP.put("startup-complete", StartupComplete::new);
        PACKETS_MAP.put("disconnect", Disconnect::new);
        PACKETS_MAP.put("new-window", NewWindow::new);
        PACKETS_MAP.put("new-override-redirect", NewWindowOverrideRedirect::new);
        PACKETS_MAP.put("draw", DrawPacket::new);
        PACKETS_MAP.put("window-metadata", WindowMetadata::new);
        PACKETS_MAP.put("lost-window", LostWindow::new);
        PACKETS_MAP.put("window-icon", WindowIcon::new);
        PACKETS_MAP.put("configure-override-redirect", ConfigureWindowOverrideRedirect::new);
        PACKETS_MAP.put("raise-window", RaiseWindow::new);
        PACKETS_MAP.put("setting-change", SettingChange::new);
        //PACKETS_MAP.put("notify_show", NotifyShow::new);
        for (String type : new String[]{"clipboard-token", "clipboard-request", "clipboard-contents",
            "clipboard-contents-none", "clipboard-pending-requests", "clipboard-enable-selections",
            "set-clipboard-enabled"}) {
            PACKETS_MAP.put(type, () -> new ClipboardPacket(type));
        }
    }

    /**
     * Packets sent by servers, which this client can safely ignore.
     */
    private static final Set<String> IGNORED_PACKETS = new HashSet<>(Arrays.asList(
        "encodings", "server-event", "ping_echo", "info-response",
        "set-cursors", "bell", "eos", "window-move-resize", "window-resized",
        "restack-window", "initiate-moveresize", "pointer-grab", "pointer-ungrab",
        "notify_show", "notify_close", "desktop_size", "control"
    ));

    public <T extends Packet> void registerHandler(Class<T> packetClass, PacketHandler<T> handler) {
        handlers.put(packetClass, handler);
    }

    public void onReceive(List<Object> dp) throws IOException {
        if (dp.size() < 1) {
            logger.error("onReceive(..) decoded data is too small: " + dp);
            return;
        }

        Iterator<Object> it = dp.iterator();
        String type = Packet.asString(it.next());
        Builder<Packet> builder = PACKETS_MAP.get(type);
        if (builder != null) {
            Packet packet = builder.build();
            try {
                packet.deserialize(it);
                logger.trace("onReceive(): " + packet);
                process(packet);
            } catch (RuntimeException e) {
                // an unexpected packet format should not bring down the whole connection
                logger.error("Failed to process packet: " + type, e);
            }
        } else if (IGNORED_PACKETS.contains(type)) {
            logger.debug("Ignoring packet: " + type);
        } else {
            logger.warn("Not supported packet: " + type);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void process(Packet packet) throws IOException {
        PacketHandler handler = handlers.get(packet.getClass());
        if (handler != null) {
            handler.process(packet);
        } else {
            logger.debug("No handler for: " + packet);
        }
    }

    private interface Builder<T> {

        T build();
    }

    public interface PacketHandler<T extends Packet> {

        void process(T packet) throws IOException;
    }
}
