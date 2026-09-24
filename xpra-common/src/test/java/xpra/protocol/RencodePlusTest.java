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

import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * The expected data was produced by Xpra's own implementation: {@code xpra.net.rencodeplus.rencodeplus.dumps()}.
 */
public class RencodePlusTest {

    @Test
    public void testIntegers() throws IOException {
        final List<Object> ints = Arrays.asList(0, 1, 43, 44, -1, -32, -33, 127, -128, 128, 32767, -32768, 32768,
            Integer.MAX_VALUE, Integer.MIN_VALUE, 1L << 31, 1L << 40, -(1L << 40), Long.MAX_VALUE);
        final String hex = "d300012b3e2c46653edf3e7f3e803f00803f7fff3f80004000008000407fffffff4080000000"
            + "41000000008000000041000001000000000041ffffff0000000000417fffffffffffffff";
        assertEncoding(hex, ints);
        final List<?> decoded = (List<?>) RencodePlus.decode(bytes(hex));
        assertEquals(ints.size(), decoded.size());
        for (int i = 0; i < ints.size(); ++i) {
            assertEquals(((Number) ints.get(i)).longValue(), ((Number) decoded.get(i)).longValue());
        }
    }

    @Test
    public void testBigInteger() throws IOException {
        final BigInteger big = BigInteger.ONE.shiftLeft(70);
        final String hex = "3d313138303539313632303731373431313330333432347f";
        assertEncoding(hex, big);
        assertEquals(big, RencodePlus.decode(bytes(hex)));
    }

    @Test
    public void testStrings() throws IOException {
        final List<Object> strings = Arrays.asList("", "a", "zażółć", repeat('x', 63), repeat('y', 64), repeat('z', 200));
        final String hex = "c68081618a7a61c5bcc3b3c582c487bf" + repeatHex("78", 63)
            + "36343a" + repeatHex("79", 64) + "3230303a" + repeatHex("7a", 200);
        assertEncoding(hex, strings);
        assertEquals(strings, RencodePlus.decode(bytes(hex)));
    }

    @Test
    public void testBytes() throws IOException {
        final List<Object> list = Arrays.asList(new byte[0], new byte[]{0, 1, (byte) 0xff}, repeat('q', 100).getBytes());
        final String hex = "c3302f332f0001ff3130302f" + repeatHex("71", 100);
        assertEncoding(hex, list);
        final List<?> decoded = (List<?>) RencodePlus.decode(bytes(hex));
        for (int i = 0; i < list.size(); ++i) {
            assertArrayEquals((byte[]) list.get(i), (byte[]) decoded.get(i));
        }
    }

    @Test
    public void testMisc() throws IOException {
        final List<Object> list = Arrays.asList(true, false, null, 1.5, -2.25);
        final String hex = "c54344452c3ff80000000000002cc002000000000000";
        assertEncoding(hex, list);
        final List<?> decoded = (List<?>) RencodePlus.decode(bytes(hex));
        assertEquals(Boolean.TRUE, decoded.get(0));
        assertEquals(Boolean.FALSE, decoded.get(1));
        assertNull(decoded.get(2));
        assertEquals(1.5, decoded.get(3));
        assertEquals(-2.25, decoded.get(4));
    }

    @Test
    public void testDict() throws IOException {
        final Map<Object, Object> nested = new LinkedHashMap<>();
        nested.put("k", Arrays.asList(1, 2, 3));
        final Map<Object, Object> dict = new LinkedHashMap<>();
        dict.put("a", 1);
        dict.put("nested", nested);
        dict.put(0, "zero");
        final String hex = "69816101866e657374656467816bc301020300847a65726f";
        assertEncoding(hex, dict);
        assertEquals(dict, RencodePlus.decode(bytes(hex)));
    }

    @Test
    public void testLongListAndDict() throws IOException {
        final List<Object> list = new ArrayList<>();
        final Map<Object, Object> dict = new LinkedHashMap<>();
        for (int i = 0; i < 70; ++i) {
            list.add(i);
        }
        for (int i = 0; i < 30; ++i) {
            dict.put("k" + i, i);
        }
        final String listHex = "3b000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b"
            + "3e2c3e2d3e2e3e2f3e303e313e323e333e343e353e363e373e383e393e3a3e3b3e3c3e3d3e3e3e3f3e403e413e423e433e443e457f";
        assertEncoding(listHex, list);
        assertEquals(list, RencodePlus.decode(bytes(listHex)));
        final String dictHex = "3c826b3000826b3101826b3202826b3303826b3404826b3505826b3606826b3707826b3808826b3909"
            + "836b31300a836b31310b836b31320c836b31330d836b31340e836b31350f836b313610836b313711836b313812836b313913"
            + "836b323014836b323115836b323216836b323317836b323418836b323519836b32361a836b32371b836b32381c836b32391d7f";
        assertEncoding(dictHex, dict);
        assertEquals(dict, RencodePlus.decode(bytes(dictHex)));
    }

    @Test
    public void testArrays() throws IOException {
        assertArrayEquals(RencodePlus.encode(Arrays.asList(Arrays.asList(1, 2), Arrays.asList(3, 4))),
            RencodePlus.encode(new int[][]{{1, 2}, {3, 4}}));
    }

    @Test(expected = IOException.class)
    public void testTruncated() throws IOException {
        RencodePlus.decode(bytes("c3302f332f0001"));
    }

    private static void assertEncoding(String expectedHex, Object value) throws IOException {
        assertEquals(expectedHex, hex(RencodePlus.encode(value)));
    }

    static byte[] bytes(String hex) {
        final byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; ++i) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String hex(byte[] data) {
        final StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    private static String repeat(char c, int count) {
        final char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String repeatHex(String hex, int count) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; ++i) {
            sb.append(hex);
        }
        return sb.toString();
    }
}
