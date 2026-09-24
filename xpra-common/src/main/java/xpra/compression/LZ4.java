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

package xpra.compression;

/**
 * Decompressor for the LZ4 data sent by Xpra servers, both for compressed packets
 * and for "rgb" pixel data: a 4-byte little-endian uncompressed size, followed by
 * a single LZ4 block.
 */
public final class LZ4 {

    /**
     * Upper limit of the decompressed size, matching Xpra's {@code MAX_DECOMPRESSED_SIZE}.
     */
    public static final int MAX_DECOMPRESSED_SIZE = 256 * 1024 * 1024;

    private static final int MIN_MATCH = 4;

    private LZ4() {
    }

    public static byte[] decompress(byte[] data) throws CompressionException {
        return decompress(data, 0, data.length);
    }

    public static byte[] decompress(byte[] data, int offset, int length) throws CompressionException {
        if (length < 4) {
            throw new CompressionException("LZ4: data too short");
        }
        final int size = (data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8
            | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24;
        if (size < 0 || size > MAX_DECOMPRESSED_SIZE) {
            throw new CompressionException("LZ4: invalid decompressed size " + (size & 0xFFFFFFFFL));
        }
        final byte[] out = new byte[size];
        final int written = decompressBlock(data, offset + 4, offset + length, out);
        if (written != size) {
            throw new CompressionException("LZ4: expected " + size + " bytes but decompressed " + written);
        }
        return out;
    }

    /**
     * Decompresses a raw LZ4 block.
     *
     * @return the number of bytes written to {@code out}
     */
    static int decompressBlock(byte[] src, int srcPos, int srcEnd, byte[] out) throws CompressionException {
        int dst = 0;
        try {
            while (srcPos < srcEnd) {
                final int token = src[srcPos++] & 0xFF;
                // literals
                int literals = token >>> 4;
                if (literals == 15) {
                    int b;
                    do {
                        b = src[srcPos++] & 0xFF;
                        literals += b;
                    } while (b == 255);
                }
                if (literals > srcEnd - srcPos || literals > out.length - dst) {
                    throw new CompressionException("LZ4: literal run out of bounds");
                }
                System.arraycopy(src, srcPos, out, dst, literals);
                srcPos += literals;
                dst += literals;
                if (srcPos >= srcEnd) {
                    // the last sequence only has literals
                    break;
                }
                // match
                final int matchOffset = (src[srcPos] & 0xFF) | (src[srcPos + 1] & 0xFF) << 8;
                srcPos += 2;
                if (matchOffset == 0 || matchOffset > dst) {
                    throw new CompressionException("LZ4: invalid match offset " + matchOffset);
                }
                int matchLength = token & 0x0F;
                if (matchLength == 15) {
                    int b;
                    do {
                        b = src[srcPos++] & 0xFF;
                        matchLength += b;
                    } while (b == 255);
                }
                matchLength += MIN_MATCH;
                if (matchLength > out.length - dst) {
                    throw new CompressionException("LZ4: match out of bounds");
                }
                int from = dst - matchOffset;
                if (matchOffset >= matchLength) {
                    System.arraycopy(out, from, out, dst, matchLength);
                    dst += matchLength;
                } else {
                    // overlapping copy: repeat byte by byte
                    for (int i = 0; i < matchLength; ++i) {
                        out[dst++] = out[from++];
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new CompressionException("LZ4: malformed data", e);
        }
        return dst;
    }
}
