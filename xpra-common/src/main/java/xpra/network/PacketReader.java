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

package xpra.network;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xpra.compression.LZ4;
import xpra.protocol.RencodePlus;

/**
 * Reads Xpra packets: an 8-byte header followed by the payload, which is either the main
 * rencodeplus-encoded packet (index 0), or a raw chunk (index &gt; 0) that replaces the item at that
 * index in the next main packet (ie: the pixel data of a "draw" packet).
 */
class PacketReader {

  /**
   * Upper limit for a single payload, to avoid allocating huge buffers on a corrupted stream.
   */
  private static final int MAX_PACKET_SIZE = 256 * 1024 * 1024;

  private final InputStream in;

  private final HeaderChunk headerChunk = new HeaderChunk();

  PacketReader(InputStream in) {
    this.in = in;
  }

  List<Object> readList() throws IOException {
    final Map<Integer, byte[]> chunks = new HashMap<>();
    while (true) {
      headerChunk.readHeader(in);
      final byte[] payload = readPayload();
      if (headerChunk.getPacketIndex() > 0) {
        chunks.put(headerChunk.getPacketIndex(), payload);
        continue;
      }
      final Object decoded = RencodePlus.decode(payload);
      if (!(decoded instanceof List)) {
        throw new IOException("invalid packet, expected a list but got " + decoded);
      }
      @SuppressWarnings("unchecked")
      final List<Object> list = (List<Object>) decoded;
      for (Map.Entry<Integer, byte[]> entry : chunks.entrySet()) {
        if (entry.getKey() >= list.size()) {
          throw new IOException("invalid chunk index " + entry.getKey() + " for a packet of size " + list.size());
        }
        list.set(entry.getKey(), entry.getValue());
      }
      return list;
    }
  }

  private byte[] readPayload() throws IOException {
    final int size = headerChunk.getPacketSize();
    if (size < 0 || size > MAX_PACKET_SIZE) {
      throw new IOException("invalid packet size: " + (size & 0xFFFFFFFFL));
    }
    final byte[] buffer = new byte[size];
    int bytesRead = 0;
    while (bytesRead < size) {
      final int r = in.read(buffer, bytesRead, size - bytesRead);
      if (r < 0) {
        throw new EOFException("Unexpected end of stream");
      }
      bytesRead += r;
    }
    if (headerChunk.isDataCompressed()) {
      return LZ4.decompress(buffer);
    }
    return buffer;
  }
}
