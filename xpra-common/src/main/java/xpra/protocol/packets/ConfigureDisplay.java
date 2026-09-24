/*
 * Copyright (C) 2017 Jakub Ksiezniak
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

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Asks the server to resize its virtual screen, ie: after the device was rotated.
 * <p>
 * Servers clamp windows to the screen size, so without this, a window made wider than the
 * screen size given at connection time is cut back and drawn stretched.
 */
public class ConfigureDisplay extends xpra.protocol.IOPacket {

    private final int width;
    private final int height;

    public ConfigureDisplay(int width, int height) {
        super("configure-display");
        this.width = width;
        this.height = height;
    }

    @Override
    protected void serialize(Collection<Object> elems) {
        final Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("desktop-size", Arrays.asList(width, height));
        attrs.put("desktop-size-unscaled", Arrays.asList(width, height));
        elems.add(attrs);
    }
}
