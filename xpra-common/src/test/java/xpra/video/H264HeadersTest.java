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

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertSame;

public class H264HeadersTest {

    /** the SPS and the start of a frame of x264 in an Xpra 6.5 server: limited range, identity matrix */
    private static final String X264_FRAME = "0000016764001eacb60501ed5080808020000003002000000651e2c5dc00" + "0000000165888400";
    /** the same, in full range with the BT.601 matrix: ffprobe says "color_range=pc, color_space=smpte170m" */
    private static final String FIXED_FRAME = "0000016764001eacb60501ed5180808320000003002000000651e2c5dc00" + "0000000165888400";

    private static byte[] hex(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    @Test
    public void testFixColours() {
        assertArrayEquals(hex(FIXED_FRAME), H264Headers.fixColours(hex(X264_FRAME), true));
    }

    @Test
    public void testAlreadyRight() {
        byte[] fixed = hex(FIXED_FRAME);
        assertSame(fixed, H264Headers.fixColours(fixed, true));
    }

    @Test
    public void testLimitedRange() {
        // only the matrix changes:
        assertArrayEquals(hex("0000016764001eacb60501ed5080808320000003002000000651e2c5dc00" + "0000000165888400"),
            H264Headers.fixColours(hex(X264_FRAME), false));
    }

    @Test
    public void testFrameWithoutSps() {
        byte[] frame = hex("0000000141e2c5dc00");
        assertSame(frame, H264Headers.fixColours(frame, true));
    }

    @Test
    public void testEscaping() {
        byte[] rbsp = hex("00000100000300ff0000");
        byte[] nal = H264Headers.escape(rbsp);
        // 00 00 followed by 00-03 gets an 03 in between
        assertArrayEquals(hex("000003010000030300ff0000"), nal);
        assertArrayEquals(rbsp, H264Headers.unescape(nal, 0, nal.length));
    }
}
