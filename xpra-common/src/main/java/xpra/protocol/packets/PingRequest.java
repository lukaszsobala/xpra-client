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

import java.util.Collection;

import xpra.protocol.IOPacket;

/**
 * A ping to the server, which answers with a "ping_echo": the connection is then never silent
 * for long while it works, see {@link xpra.network.TcpXpraConnector#READ_TIMEOUT_MS}.
 */
public class PingRequest extends IOPacket {

    private final long time = System.nanoTime() / 1_000_000;
    private final long wallTime = System.currentTimeMillis();

    public PingRequest() {
        super("ping");
    }

    @Override
    protected void serialize(Collection<Object> elems) {
        elems.add(time);
        elems.add(wallTime);
    }
}
