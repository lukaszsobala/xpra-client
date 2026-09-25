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

package xpra.video;

import java.io.ByteArrayOutputStream;

/**
 * Corrects the colour description of H.264 streams, in their sequence parameter sets (SPS).
 * <p>
 * Decoders convert the frames to RGB as their SPS says, but x264 in Xpra servers describes the
 * colours wrongly: the frames are converted from RGB with the BT.601 matrix, in full range,
 * while the SPS says "limited range" (the encoder starts with it and switches to full range
 * later) and "identity matrix" (as if the planes held RGB). The frames then look too dark, with
 * clipped whites. The frames tell the range in their options ("full-range").
 */
public final class H264Headers {

    /** BT.601, as used by the servers' colourspace converters */
    public static final int MATRIX_BT601 = 6;
    /** the "identity" matrix: the planes hold G, B and R */
    private static final int MATRIX_IDENTITY = 0;

    private static final int NAL_SPS = 7;
    /** the SPS comes at the start of the frames which have one */
    private static final int SCAN_LIMIT = 1024;

    private H264Headers() {
    }

    /**
     * @param frame - a frame of an H.264 stream, with start codes (Annex B)
     * @param fullRange - whether the frame is in full range
     * @return the frame with its SPS corrected, or the same frame
     */
    public static byte[] fixColours(byte[] frame, boolean fullRange) {
        return fixColours(frame, fullRange, SCAN_LIMIT);
    }

    static byte[] fixColours(byte[] frame, boolean fullRange, int scanLimit) {
        final int limit = Math.min(frame.length, scanLimit);
        for (int i = 0; i + 3 < limit; ++i) {
            if (frame[i] != 0 || frame[i + 1] != 0 || frame[i + 2] != 1) {
                continue;
            }
            final int start = i + 3;
            if ((frame[start] & 0x1f) != NAL_SPS) {
                continue;
            }
            final int end = nextStartCode(frame, start);
            final byte[] rbsp = unescape(frame, start + 1, end);
            if (!fixSps(rbsp, fullRange)) {
                return frame;
            }
            final byte[] nal = escape(rbsp);
            final ByteArrayOutputStream out = new ByteArrayOutputStream(frame.length + 8);
            out.write(frame, 0, start + 1);
            out.write(nal, 0, nal.length);
            out.write(frame, end, frame.length - end);
            return out.toByteArray();
        }
        return frame;
    }

    /**
     * @return the index of the next start code (or its leading zero), or the end of the frame
     */
    private static int nextStartCode(byte[] data, int from) {
        for (int i = from; i + 2 < data.length; ++i) {
            if (data[i] == 0 && data[i + 1] == 0 && (data[i + 2] == 1 || (data[i + 2] == 0 && i + 3 < data.length && data[i + 3] == 1))) {
                return i;
            }
        }
        return data.length;
    }

    /** removes the emulation prevention bytes: 00 00 03 -> 00 00 */
    static byte[] unescape(byte[] data, int from, int to) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(to - from);
        int zeros = 0;
        for (int i = from; i < to; ++i) {
            final int b = data[i] & 0xff;
            if (zeros >= 2 && b == 3) {
                zeros = 0;
                continue;
            }
            out.write(b);
            zeros = b == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    /** adds the emulation prevention bytes */
    static byte[] escape(byte[] rbsp) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(rbsp.length + 8);
        int zeros = 0;
        for (byte value : rbsp) {
            final int b = value & 0xff;
            if (zeros >= 2 && b <= 3) {
                out.write(3);
                zeros = 0;
            }
            out.write(b);
            zeros = b == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    /**
     * Sets the range, and the matrix if it is the identity, in the VUI of the SPS.
     *
     * @return false if the SPS does not describe its colours: nothing to fix then
     */
    static boolean fixSps(byte[] rbsp, boolean fullRange) {
        final BitReader r = new BitReader(rbsp);
        try {
            final int profile = r.bits(8);
            r.bits(16); // constraints, level
            r.ue(); // sps id
            if (profile == 100 || profile == 110 || profile == 122 || profile == 244 || profile == 44
                || profile == 83 || profile == 86 || profile == 118 || profile == 128 || profile == 138
                || profile == 139 || profile == 134 || profile == 135) {
                final int chroma = r.ue();
                if (chroma == 3) {
                    r.bits(1);
                }
                r.ue(); // luma bit depth
                r.ue(); // chroma bit depth
                r.bits(1); // qpprime y zero transform bypass
                if (r.bits(1) == 1) {
                    for (int i = 0; i < (chroma == 3 ? 12 : 8); ++i) {
                        if (r.bits(1) == 1) {
                            skipScalingList(r, i < 6 ? 16 : 64);
                        }
                    }
                }
            }
            r.ue(); // log2 max frame num
            final int pocType = r.ue();
            if (pocType == 0) {
                r.ue();
            } else if (pocType == 1) {
                r.bits(1);
                r.se();
                r.se();
                final int cycle = r.ue();
                for (int i = 0; i < cycle; ++i) {
                    r.se();
                }
            }
            r.ue(); // max num ref frames
            r.bits(1); // gaps
            r.ue(); // width
            r.ue(); // height
            if (r.bits(1) == 0) { // frame mbs only
                r.bits(1);
            }
            r.bits(1); // direct 8x8
            if (r.bits(1) == 1) { // cropping
                r.ue();
                r.ue();
                r.ue();
                r.ue();
            }
            if (r.bits(1) == 0) { // no VUI
                return false;
            }
            if (r.bits(1) == 1) { // aspect ratio
                if (r.bits(8) == 255) {
                    r.bits(32);
                }
            }
            if (r.bits(1) == 1) { // overscan
                r.bits(1);
            }
            if (r.bits(1) == 0) { // no video signal type
                return false;
            }
            r.bits(3); // video format
            final int rangePosition = r.position();
            r.bits(1);
            boolean changed = setBits(rbsp, rangePosition, 1, fullRange ? 1 : 0);
            if (r.bits(1) == 1) { // colour description
                r.bits(16); // primaries, transfer
                final int matrixPosition = r.position();
                if (r.bits(8) == MATRIX_IDENTITY) {
                    changed |= setBits(rbsp, matrixPosition, 8, MATRIX_BT601);
                }
            }
            return changed;
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    private static void skipScalingList(BitReader r, int size) {
        int last = 8;
        int next = 8;
        for (int j = 0; j < size; ++j) {
            if (next != 0) {
                next = (last + r.se() + 256) % 256;
            }
            last = next == 0 ? last : next;
        }
    }

    private static boolean setBits(byte[] data, int position, int count, int value) {
        boolean changed = false;
        for (int i = 0; i < count; ++i) {
            final int bit = (value >> (count - 1 - i)) & 1;
            final int index = (position + i) >> 3;
            final int mask = 0x80 >> ((position + i) & 7);
            final int old = data[index] & mask;
            if ((old != 0) != (bit == 1)) {
                data[index] = (byte) (data[index] ^ mask);
                changed = true;
            }
        }
        return changed;
    }

    static final class BitReader {
        private final byte[] data;
        private int position;

        BitReader(byte[] data) {
            this.data = data;
        }

        int position() {
            return position;
        }

        int bits(int count) {
            int value = 0;
            for (int i = 0; i < count; ++i) {
                final int b = (data[position >> 3] >> (7 - (position & 7))) & 1;
                value = (value << 1) | b;
                ++position;
            }
            return value;
        }

        int ue() {
            int zeros = 0;
            while (bits(1) == 0) {
                ++zeros;
                if (zeros > 31) {
                    throw new IndexOutOfBoundsException("bad exp-golomb code");
                }
            }
            return zeros == 0 ? 0 : (1 << zeros) - 1 + bits(zeros);
        }

        int se() {
            final int k = ue();
            return (k & 1) == 1 ? (k + 1) / 2 : -(k / 2);
        }
    }
}
