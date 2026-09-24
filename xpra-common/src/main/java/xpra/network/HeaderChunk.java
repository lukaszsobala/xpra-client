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

package xpra.network;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class HeaderChunk {

    // protocol flags (header byte 1)
    public static final int FLAG_RENCODE = 0x1;
    public static final int FLAG_CIPHER = 0x2;
    public static final int FLAG_YAML = 0x4;
    public static final int FLAG_FLUSH = 0x8;
    public static final int FLAG_RENCODEPLUS = 0x10;

    // compressor flags (upper bits of header byte 2, the lower 4 bits hold the level)
    public static final int COMPRESSION_LEVEL_MASK = 0x0F;
    public static final int COMPRESSOR_LZ4 = 0x10;
    public static final int COMPRESSOR_BROTLI = 0x40;
    public static final int COMPRESSOR_ZSTD = 0x80;

    private static final int HEADER_SIZE = 8;
    private static final byte MAGIC_BYTE = 'P';

    private final byte[] header = new byte[HEADER_SIZE];


    public HeaderChunk() {
        header[0] = MAGIC_BYTE;
    }

    void readHeader(InputStream is) throws IOException {
        int headerRead = 0;
        while (headerRead < HEADER_SIZE) {
            final int bytesRead = is.read(header, headerRead, HEADER_SIZE - headerRead);
            if (bytesRead < 0) {
                throw new EOFException("Failed to read header.");
            }
            headerRead += bytesRead;
        }
        if (header[0] != MAGIC_BYTE) {
            // this is usually an error message printed by the remote command (ie: over SSH)
            throw new IOException("Invalid data received from the server: "
                + new String(header, java.nio.charset.StandardCharsets.UTF_8).trim());
        }
        if (hasFlags(~(FLAG_RENCODEPLUS | FLAG_FLUSH))) {
            throw new IOException("unsupported protocol flags: 0x" + Integer.toHexString(getFlags() & 0xFF));
        }
        // lz4 is the only compressor we advertise
        final int compressor = getCompressionLevel() & 0xF0;
        if (isDataCompressed() && compressor != COMPRESSOR_LZ4) {
            throw new IOException("unsupported compressor: 0x" + Integer.toHexString(compressor));
        }
    }

    public void writeHeader(OutputStream outputStream) throws IOException {
        outputStream.write(header);
    }

    byte getFlags() {
        return header[1];
    }

    public void setFlags(int flags) {
        header[1] = (byte) flags;
    }

    boolean hasFlags(int flags) {
        return (getFlags() & flags) != 0;
    }

    byte getCompressionLevel() {
        return header[2];
    }

    void setCompressionLevel(int level) {
        header[2] = (byte) level;
    }

    boolean isDataCompressed() {
        return getCompressionLevel() != 0;
    }

    int getPacketIndex() {
        return header[3] & 0xFF;
    }

    void setPacketIndex(int packetIndex) {
        header[3] = (byte) packetIndex;
    }

    int getPacketSize() {
        return (header[4] & 0xFF) << 24 | (header[5] & 0xFF) << 16 | (header[6] & 0xFF) << 8 | (header[7] & 0xFF);
    }

    public void setPacketSize(int packetSize) {
        header[4] = (byte) ((packetSize >>> 24) & 0xFF);
        header[5] = (byte) ((packetSize >>> 16) & 0xFF);
        header[6] = (byte) ((packetSize >>> 8) & 0xFF);
        header[7] = (byte) (packetSize & 0xFF);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + Arrays.toString(header);
    }
}