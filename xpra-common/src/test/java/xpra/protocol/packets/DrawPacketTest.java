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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xpra.compression.CompressionException;
import xpra.protocol.PictureEncoding;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DrawPacketTest {

    private static final byte[] PACKED = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18};

    @Test
    public void testPaddedRowsAreRepacked() throws CompressionException {
        final byte[] padded = new byte[24];
        Arrays.fill(padded, (byte) 0xee);
        System.arraycopy(PACKED, 0, padded, 0, 9);
        System.arraycopy(PACKED, 9, padded, 12, 9);
        final DrawPacket packet = draw(padded, 12, Collections.<String, Object>emptyMap());
        assertArrayEquals(PACKED, packet.readPixels());
        assertEquals("RGB", packet.getRgbFormat());
    }

    /**
     * The compressed data was produced by Xpra's own {@code xpra.net.lz4.lz4.compress()}.
     */
    @Test
    public void testLz4CompressedPaddedRows() throws CompressionException {
        final Map<String, Object> options = new HashMap<>();
        options.put("lz4", 1);
        options.put("rgb_format", "RGB");
        final DrawPacket packet = draw(bytes("18000000f009010203040506070809eeeeee0a0b0c0d0e0f101112eeeeee"), 12, options);
        assertArrayEquals(PACKED, packet.readPixels());
    }

    @Test
    public void testPackedRows() throws CompressionException {
        final DrawPacket packet = draw(PACKED, 9, Collections.<String, Object>emptyMap());
        assertArrayEquals(PACKED, packet.readPixels());
    }

    @Test
    public void testWindowSize() {
        final Map<String, Object> options = new HashMap<>();
        options.put("window-size", Arrays.asList(484, 316));
        assertArrayEquals(new int[]{484, 316}, draw(PACKED, 9, options).getWindowSize());
        assertNull(draw(PACKED, 9, Collections.<String, Object>emptyMap()).getWindowSize());
    }

    private static DrawPacket draw(byte[] data, int rowstride, Map<String, Object> options) {
        final List<Object> fields = Arrays.<Object>asList(1, 0, 0, 3, 2, PictureEncoding.rgb24.toString(), data, 7, rowstride, options);
        final DrawPacket packet = new DrawPacket();
        packet.deserialize(fields.iterator());
        return packet;
    }

    private static byte[] bytes(String hex) {
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; ++i) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
