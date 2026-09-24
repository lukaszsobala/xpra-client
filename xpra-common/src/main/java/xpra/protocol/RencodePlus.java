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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "rencodeplus" packet encoding, which is the only one accepted by Xpra servers since v5.
 * <p>
 * It is a variant of rencode, which distinguishes text strings ("{@code len:}" prefix or
 * fixed-length typecodes) from binary data ("{@code len/}" prefix), and uses big-endian numbers.
 * See {@code xpra/net/rencodeplus/rencodeplus.pyx} in the Xpra sources.
 * <p>
 * Decoded values are mapped to: {@link Integer}, {@link Long} or {@link BigInteger} for integers,
 * {@link Float} or {@link Double}, {@link String} for text, {@code byte[]} for binary data,
 * {@link Boolean}, {@code null}, {@link List} and {@link Map}.
 */
public final class RencodePlus {

    private static final int CHR_LIST = 59;
    private static final int CHR_DICT = 60;
    private static final int CHR_INT = 61;
    private static final int CHR_INT1 = 62;
    private static final int CHR_INT2 = 63;
    private static final int CHR_INT4 = 64;
    private static final int CHR_INT8 = 65;
    private static final int CHR_FLOAT32 = 66;
    private static final int CHR_FLOAT64 = 44;
    private static final int CHR_TRUE = 67;
    private static final int CHR_FALSE = 68;
    private static final int CHR_NONE = 69;
    private static final int CHR_TERM = 127;

    private static final int INT_POS_FIXED_START = 0;
    private static final int INT_POS_FIXED_COUNT = 44;
    private static final int INT_NEG_FIXED_START = 70;
    private static final int INT_NEG_FIXED_COUNT = 32;
    private static final int DICT_FIXED_START = 102;
    private static final int DICT_FIXED_COUNT = 25;
    private static final int STR_FIXED_START = 128;
    private static final int STR_FIXED_COUNT = 64;
    private static final int LIST_FIXED_START = STR_FIXED_START + STR_FIXED_COUNT;
    private static final int LIST_FIXED_COUNT = 64;

    private static final int MAX_INT_LENGTH = 64;

    private RencodePlus() {
    }

    // ---------------------------------------------------------------- encoding

    public static byte[] encode(Object value) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        encode(out, value);
        return out.toByteArray();
    }

    public static void encode(ByteArrayOutputStream out, Object value) throws IOException {
        if (value == null) {
            out.write(CHR_NONE);
        } else if (value instanceof Boolean) {
            out.write((Boolean) value ? CHR_TRUE : CHR_FALSE);
        } else if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
            encodeLong(out, ((Number) value).longValue());
        } else if (value instanceof BigInteger) {
            encodeBigInteger(out, (BigInteger) value);
        } else if (value instanceof Float || value instanceof Double) {
            out.write(CHR_FLOAT64);
            writeLong(out, Double.doubleToLongBits(((Number) value).doubleValue()));
        } else if (value instanceof String) {
            encodeString(out, (String) value);
        } else if (value instanceof byte[]) {
            final byte[] bytes = (byte[]) value;
            out.write((bytes.length + "/").getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
        } else if (value instanceof Map) {
            encodeMap(out, (Map<?, ?>) value);
        } else if (value instanceof Collection) {
            encodeList(out, new ArrayList<>((Collection<?>) value));
        } else if (value.getClass().isArray()) {
            final int length = Array.getLength(value);
            final List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; ++i) {
                list.add(Array.get(value, i));
            }
            encodeList(out, list);
        } else if (value instanceof Enum) {
            encodeString(out, value.toString());
        } else {
            throw new IOException("rencodeplus: unsupported type " + value.getClass().getName());
        }
    }

    private static void encodeLong(ByteArrayOutputStream out, long x) {
        if (x >= 0 && x < INT_POS_FIXED_COUNT) {
            out.write(INT_POS_FIXED_START + (int) x);
        } else if (x >= -INT_NEG_FIXED_COUNT && x < 0) {
            out.write(INT_NEG_FIXED_START - 1 - (int) x);
        } else if (x >= Byte.MIN_VALUE && x <= Byte.MAX_VALUE) {
            out.write(CHR_INT1);
            out.write((int) x);
        } else if (x >= Short.MIN_VALUE && x <= Short.MAX_VALUE) {
            out.write(CHR_INT2);
            out.write((int) (x >> 8));
            out.write((int) x);
        } else if (x >= Integer.MIN_VALUE && x <= Integer.MAX_VALUE) {
            out.write(CHR_INT4);
            writeInt(out, (int) x);
        } else {
            out.write(CHR_INT8);
            writeLong(out, x);
        }
    }

    private static void encodeBigInteger(ByteArrayOutputStream out, BigInteger x) throws IOException {
        if (x.bitLength() < 64) {
            encodeLong(out, x.longValue());
            return;
        }
        final byte[] digits = x.toString().getBytes(StandardCharsets.US_ASCII);
        if (digits.length >= MAX_INT_LENGTH) {
            throw new IOException("rencodeplus: number is longer than " + MAX_INT_LENGTH + " characters");
        }
        out.write(CHR_INT);
        out.write(digits);
        out.write(CHR_TERM);
    }

    private static void encodeString(ByteArrayOutputStream out, String s) throws IOException {
        final byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        if (utf8.length < STR_FIXED_COUNT) {
            out.write(STR_FIXED_START + utf8.length);
        } else {
            out.write((utf8.length + ":").getBytes(StandardCharsets.US_ASCII));
        }
        out.write(utf8);
    }

    private static void encodeList(ByteArrayOutputStream out, List<?> list) throws IOException {
        final boolean fixed = list.size() < LIST_FIXED_COUNT;
        out.write(fixed ? LIST_FIXED_START + list.size() : CHR_LIST);
        for (Object item : list) {
            encode(out, item);
        }
        if (!fixed) {
            out.write(CHR_TERM);
        }
    }

    private static void encodeMap(ByteArrayOutputStream out, Map<?, ?> map) throws IOException {
        final boolean fixed = map.size() < DICT_FIXED_COUNT;
        out.write(fixed ? DICT_FIXED_START + map.size() : CHR_DICT);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            encode(out, entry.getKey());
            encode(out, entry.getValue());
        }
        if (!fixed) {
            out.write(CHR_TERM);
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int x) {
        out.write(x >>> 24);
        out.write(x >>> 16);
        out.write(x >>> 8);
        out.write(x);
    }

    private static void writeLong(ByteArrayOutputStream out, long x) {
        writeInt(out, (int) (x >>> 32));
        writeInt(out, (int) x);
    }

    // ---------------------------------------------------------------- decoding

    public static Object decode(byte[] data) throws IOException {
        return decode(data, 0, data.length);
    }

    public static Object decode(byte[] data, int offset, int length) throws IOException {
        final Decoder decoder = new Decoder(data, offset, offset + length);
        return decoder.decode();
    }

    private static final class Decoder {
        private final byte[] data;
        private final int end;
        private int pos;

        Decoder(byte[] data, int offset, int end) {
            this.data = data;
            this.pos = offset;
            this.end = end;
        }

        private int peek() throws IOException {
            check(pos);
            return data[pos] & 0xFF;
        }

        private void check(int index) throws IOException {
            if (index >= end) {
                throw new IOException("rencodeplus: truncated data at offset " + index);
            }
        }

        Object decode() throws IOException {
            final int typecode = peek();
            switch (typecode) {
                case CHR_INT1:
                    check(pos + 1);
                    pos += 2;
                    return (int) data[pos - 1];
                case CHR_INT2:
                    check(pos + 2);
                    pos += 3;
                    return (int) (short) (((data[pos - 2] & 0xFF) << 8) | (data[pos - 1] & 0xFF));
                case CHR_INT4:
                    check(pos + 4);
                    pos += 5;
                    return readInt(pos - 4);
                case CHR_INT8: {
                    check(pos + 8);
                    pos += 9;
                    final long value = readLong(pos - 8);
                    if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                        return (int) value;
                    }
                    return value;
                }
                case CHR_INT:
                    return decodeBigNumber();
                case CHR_FLOAT32:
                    check(pos + 4);
                    pos += 5;
                    return Float.intBitsToFloat(readInt(pos - 4));
                case CHR_FLOAT64:
                    check(pos + 8);
                    pos += 9;
                    return Double.longBitsToDouble(readLong(pos - 8));
                case CHR_NONE:
                    pos++;
                    return null;
                case CHR_TRUE:
                    pos++;
                    return Boolean.TRUE;
                case CHR_FALSE:
                    pos++;
                    return Boolean.FALSE;
                case CHR_LIST: {
                    pos++;
                    final List<Object> list = new ArrayList<>();
                    while (peek() != CHR_TERM) {
                        list.add(decode());
                    }
                    pos++;
                    return list;
                }
                case CHR_DICT: {
                    pos++;
                    final Map<Object, Object> map = new LinkedHashMap<>();
                    while (peek() != CHR_TERM) {
                        final Object key = decode();
                        map.put(key, decode());
                    }
                    pos++;
                    return map;
                }
                default:
                    break;
            }
            if (typecode < INT_POS_FIXED_START + INT_POS_FIXED_COUNT) {
                pos++;
                return typecode - INT_POS_FIXED_START;
            }
            if (typecode >= INT_NEG_FIXED_START && typecode < INT_NEG_FIXED_START + INT_NEG_FIXED_COUNT) {
                pos++;
                return -(typecode - INT_NEG_FIXED_START + 1);
            }
            if (typecode >= '0' && typecode <= '9') {
                return decodeLengthPrefixed();
            }
            if (typecode >= DICT_FIXED_START && typecode < DICT_FIXED_START + DICT_FIXED_COUNT) {
                final int size = typecode - DICT_FIXED_START;
                pos++;
                final Map<Object, Object> map = new LinkedHashMap<>(size * 2);
                for (int i = 0; i < size; ++i) {
                    final Object key = decode();
                    map.put(key, decode());
                }
                return map;
            }
            if (typecode >= STR_FIXED_START && typecode < STR_FIXED_START + STR_FIXED_COUNT) {
                final int size = typecode - STR_FIXED_START;
                check(pos + size);
                final String s = new String(data, pos + 1, size, StandardCharsets.UTF_8);
                pos += size + 1;
                return s;
            }
            if (typecode >= LIST_FIXED_START) {
                final int size = typecode - LIST_FIXED_START;
                pos++;
                final List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; ++i) {
                    list.add(decode());
                }
                return list;
            }
            throw new IOException("rencodeplus: unsupported typecode " + typecode + " at offset " + pos);
        }

        private Object decodeLengthPrefixed() throws IOException {
            int x = pos;
            int size = 0;
            while (true) {
                check(x);
                final int c = data[x] & 0xFF;
                if (c == ':' || c == '/') {
                    break;
                }
                if (c < '0' || c > '9' || x - pos > 10) {
                    throw new IOException("rencodeplus: invalid string length at offset " + pos);
                }
                size = size * 10 + (c - '0');
                x++;
            }
            final boolean binary = data[x] == '/';
            final int start = x + 1;
            if (size > 0) {
                check(start + size - 1);
            }
            pos = start + size;
            if (binary) {
                final byte[] bytes = new byte[size];
                System.arraycopy(data, start, bytes, 0, size);
                return bytes;
            }
            return new String(data, start, size, StandardCharsets.UTF_8);
        }

        private Object decodeBigNumber() throws IOException {
            final int start = pos + 1;
            int x = start;
            while (peekAt(x) != CHR_TERM) {
                x++;
                if (x - start >= MAX_INT_LENGTH) {
                    throw new IOException("rencodeplus: number is longer than " + MAX_INT_LENGTH + " characters");
                }
            }
            final BigInteger value = new BigInteger(new String(data, start, x - start, StandardCharsets.US_ASCII));
            pos = x + 1;
            if (value.bitLength() < 64) {
                return value.longValue();
            }
            return value;
        }

        private int peekAt(int index) throws IOException {
            check(index);
            return data[index] & 0xFF;
        }

        private int readInt(int index) {
            return (data[index] & 0xFF) << 24 | (data[index + 1] & 0xFF) << 16
                | (data[index + 2] & 0xFF) << 8 | (data[index + 3] & 0xFF);
        }

        private long readLong(int index) {
            return ((long) readInt(index) << 32) | (readInt(index + 4) & 0xFFFFFFFFL);
        }
    }
}
