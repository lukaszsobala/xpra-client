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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xpra.network.HeaderChunk;

/**
 * Sends packets to the server from a background thread, encoded with rencodeplus.
 * Packets sent by a client are small, so they are never compressed.
 */
public final class XpraSender implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(XpraSender.class);

    private final OutputStream outputStream;
    private final SendWorker sendWorker;

    public XpraSender(OutputStream os) {
        this.outputStream = os;
        this.sendWorker = new SendWorker();
        this.sendWorker.setDaemon(true);
        this.sendWorker.start();
    }

    public synchronized void send(IOPacket packet) {
        if (!sendWorker.isAlive()) {
            logger.warn("Stream closed! Failed to send packet: " + packet.type);
            return;
        }
        final ArrayList<Object> list = new ArrayList<>();
        list.add(packet.type);
        packet.serialize(list);
        sendWorker.queue.offer(list);
    }

    @Override
    public void close() throws IOException {
        sendWorker.interrupt();
        try {
            sendWorker.join();
        } catch (InterruptedException e) {
            // unused
        }
    }

    private class SendWorker extends Thread {
        private final HeaderChunk headerChunk = new HeaderChunk();
        private final UnsafeByteArrayOutputStream byteStream = new UnsafeByteArrayOutputStream(4096);
        private final BlockingQueue<ArrayList<Object>> queue = new LinkedBlockingQueue<>();

        SendWorker() {
            super("XpraSender");
            headerChunk.setFlags(HeaderChunk.FLAG_RENCODEPLUS);
        }

        @Override
        public void run() {
            try {
                while (!Thread.interrupted()) {
                    ArrayList<Object> list = queue.take();
                    send(list);
                }
            } catch (InterruptedException e) {
                logger.debug("Finished sender thread.");
            }
        }

        private void send(ArrayList<Object> list) {
            try {
                byteStream.reset();
                RencodePlus.encode(byteStream, list);
                final int packetSize = byteStream.size();
                headerChunk.setPacketSize(packetSize);
                logger.trace("send(" + list + ")");
                headerChunk.writeHeader(outputStream);
                outputStream.write(byteStream.getBytes(), 0, packetSize);
                outputStream.flush();
            } catch (IOException e) {
                logger.error("Failed to send packet: " + list.get(0), e);
            }
        }
    }
}
