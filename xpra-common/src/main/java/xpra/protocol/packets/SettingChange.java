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

import java.util.Iterator;

import xpra.protocol.Packet;

/**
 * A server setting that changed after the handshake, ie: the application menu ("menu", or
 * "xdg-menu" on servers older than 6.4).
 */
public class SettingChange extends Packet {

    private String setting;
    private Object value;

    @Override
    public void deserialize(Iterator<Object> iter) {
        super.deserialize(iter);
        setting = asString(iter.next());
        value = iter.hasNext() ? iter.next() : null;
    }

    public String getSetting() {
        return setting;
    }

    public Object getValue() {
        return value;
    }
}
