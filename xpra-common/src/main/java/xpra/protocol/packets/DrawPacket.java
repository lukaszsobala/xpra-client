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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import xpra.compression.CompressionException;
import xpra.compression.LZ4;
import xpra.protocol.PictureEncoding;

public class DrawPacket extends WindowPacket {

    public int x;
    public int y;
    public int w;
    public int h;
    public PictureEncoding encoding;
    public byte[] data;
    public int packet_sequence;
    public int rowstride;

    public Map<String, Object> options = new HashMap<>();

    public DrawPacket() {
        super("draw");
    }

    @Override
    public void deserialize(Iterator<Object> iter) {
        super.deserialize(iter);
        x = asInt(iter.next());
        y = asInt(iter.next());
        w = asInt(iter.next());
        h = asInt(iter.next());
        encoding = PictureEncoding.decode(asString(iter.next()));
        data = asByteArray(iter.next());
        packet_sequence = asInt(iter.next());
        rowstride = asInt(iter.next());
        if (iter.hasNext()) {
            options = asMap(iter.next());
        }
    }

    /**
     * Returns the pixels of an "rgb24" or "rgb32" update, decompressed and tightly packed
     * ({@code w * bytesPerPixel} bytes per row), in the {@link #getRgbFormat() rgb format} sent by the server.
     */
    public byte[] readPixels() throws CompressionException {
        byte[] pixels = data;
        if (options.containsKey("lz4")) {
            pixels = LZ4.decompress(pixels);
        } else if (options.containsKey("zlib") || options.containsKey("brotli")) {
            throw new CompressionException("unsupported pixel compression: " + options.keySet());
        }
        final int bytesPerPixel = encoding == PictureEncoding.rgb24 ? 3 : 4;
        final int packedStride = w * bytesPerPixel;
        if (rowstride <= packedStride || h <= 1) {
            return pixels;
        }
        // remove the padding at the end of each row:
        final byte[] packed = new byte[packedStride * h];
        for (int row = 0; row < h; ++row) {
            System.arraycopy(pixels, row * rowstride, packed, row * packedStride, packedStride);
        }
        return packed;
    }

    /**
     * Returns the pixels of an "rgb24" or "rgb32" update as tightly packed RGBA,
     * converting the other {@link #getRgbFormat() rgb formats} and making "X" padding opaque.
     */
    public byte[] readRgbaPixels() throws CompressionException {
        final byte[] pixels = readPixels();
        final String format = getRgbFormat();
        if ("RGBA".equals(format)) {
            return pixels;
        }
        final int bytesPerPixel = format.length();
        final int red = format.indexOf('R');
        final int green = format.indexOf('G');
        final int blue = format.indexOf('B');
        final int alpha = format.indexOf('A');
        if ((bytesPerPixel != 3 && bytesPerPixel != 4) || red < 0 || green < 0 || blue < 0) {
            throw new CompressionException("unsupported rgb format: " + format);
        }
        final int count = pixels.length / bytesPerPixel;
        final byte[] rgba = new byte[count * 4];
        for (int i = 0, src = 0, dst = 0; i < count; ++i, src += bytesPerPixel, dst += 4) {
            rgba[dst] = pixels[src + red];
            rgba[dst + 1] = pixels[src + green];
            rgba[dst + 2] = pixels[src + blue];
            rgba[dst + 3] = alpha >= 0 ? pixels[src + alpha] : (byte) 0xFF;
        }
        return rgba;
    }

    /**
     * @return the size of the window when the server sent this update, as {@code {width, height}},
     * or {@code null} if the server did not include it
     */
    public int[] getWindowSize() {
        final Object size = options.get("window-size");
        if (size instanceof List && ((List<?>) size).size() >= 2) {
            final List<?> list = (List<?>) size;
            return new int[]{asInt(list.get(0)), asInt(list.get(1))};
        }
        return null;
    }

    /**
     * @return the pixel format of "rgb24" and "rgb32" updates, ie: "RGB", "RGBX" or "RGBA"
     */
    public String getRgbFormat() {
        final Object format = options.get("rgb_format");
        if (format != null) {
            return asString(format);
        }
        return encoding == PictureEncoding.rgb24 ? "RGB" : "RGBX";
    }

    /**
     * The size of a video frame, which the server may have scaled down from the size of the
     * area to paint: the frame is then stretched over it.
     */
    public int[] getVideoSize() {
        final Object size = options.get("scaled_size");
        if (size instanceof List && ((List<?>) size).size() >= 2) {
            final List<?> list = (List<?>) size;
            return new int[]{((Number) list.get(0)).intValue(), ((Number) list.get(1)).intValue()};
        }
        return new int[]{w, h};
    }

    /**
     * The number of a video frame in its stream, from 0 for the first one, or -1.
     */
    public int getFrame() {
        final Object frame = options.get("frame");
        return frame instanceof Number ? ((Number) frame).intValue() : -1;
    }

    /**
     * Whether the (video) frame must be shown: some are only decoded, to keep the stream going,
     * when newer pictures were painted already.
     */
    public boolean isPainted() {
        final Object paint = options.get("paint");
        return paint == null || asBoolean(paint);
    }

    public String getOption(String key) {
        return asString(options.get(key));
    }

    @Override
    public String toString() {
        return String.format("%s(%d, %dx%d, %dx%d, %s, opts=%s)", getClass().getSimpleName(), windowId, x, y, w, h, encoding, options.keySet());
    }
}
