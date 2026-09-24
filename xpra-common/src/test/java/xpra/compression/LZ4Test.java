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

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;

/**
 * The compressed data was produced by Xpra's own implementation: {@code xpra.net.lz4.lz4.compress()}.
 */
public class LZ4Test {

    private static final String COMPRESSED = "58160000fff1000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
        + "202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455"
        + "565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b"
        + "8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1"
        + "c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7"
        + "f8f9fafbfcfdfeff0001ffffffffffffffffffffffffffffffffffffff00cf68656c6c6f20776f726c64200c00ffff36506f726c6420";

    @Test
    public void testDecompress() throws CompressionException {
        final ByteArrayOutputStream expected = new ByteArrayOutputStream();
        for (int i = 0; i < 20; ++i) {
            for (int b = 0; b < 256; ++b) {
                expected.write(b);
            }
        }
        for (int i = 0; i < 50; ++i) {
            expected.writeBytes("hello world ".getBytes(StandardCharsets.US_ASCII));
        }
        assertArrayEquals(expected.toByteArray(), LZ4.decompress(bytes(COMPRESSED)));
    }

    @Test(expected = CompressionException.class)
    public void testTruncated() throws CompressionException {
        LZ4.decompress(bytes(COMPRESSED.substring(0, 100)));
    }

    @Test(expected = CompressionException.class)
    public void testInvalidSize() throws CompressionException {
        LZ4.decompress(bytes("ffffffff00"));
    }

    private static byte[] bytes(String hex) {
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; ++i) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
