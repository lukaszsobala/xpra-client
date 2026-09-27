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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xpra.client.XpraClient;
import xpra.client.XpraConnector;
import xpra.protocol.packets.Disconnect;

public class TcpXpraConnector extends XpraConnector implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TcpXpraConnector.class);

    /**
     * A connection without any packet for this long is dead: without this, a connection lost
     * without being closed, ie: when the network changed, would only be noticed hours later.
     */
    public static final int READ_TIMEOUT_MS = 30_000;
    /** without it, connecting to an unreachable server waits for minutes, ie: while reconnecting */
    public static final int CONNECT_TIMEOUT_MS = 15_000;
    /** how long the server has to close the connection, once told that the client disconnects */
    static final int DISCONNECT_GRACE_MS = 2_000;

    private final String host;
    private final int port;

    private volatile Thread thread;
    /** the thread of the connection, until it ends: see {@link #isAlive()} */
    private volatile Thread worker;
    private volatile Socket socket;

    public TcpXpraConnector(XpraClient client, String hostname, int port) {
        super(client);
        this.host = hostname;
        this.port = port;
    }

    @Override
    public synchronized boolean connect() {
        if (thread != null) {
            return false;
        }
        thread = new Thread(this, "XpraTcpConnection");
        worker = thread;
        thread.start();
        return true;
    }

    @Override
    public synchronized void disconnect() {
        final Thread t = thread;
        if (t != null) {
            // before closing the socket, which the thread checks once it created it
            thread = null;
            if (disconnectCleanly()) {
                // a server which cannot be reached any more never closes the connection
                Later.run(this::closeSocket, DISCONNECT_GRACE_MS);
            } else {
                // still connecting, which an interruption does not stop
                t.interrupt();
                Later.run(this::closeSocket, 0);
            }
        }
    }

    private void closeSocket() {
        final Socket s = socket;
        if (s != null) try {
            s.close();
        } catch (IOException ignored) {
        }
    }

    private boolean disconnectCleanly() {
        final xpra.protocol.XpraSender s = client.getSender();
        if (s != null) {
            s.send(new Disconnect());
            return true;
        }
        return false;
    }

    @Override
    public void run() {
        Socket socket = null;
        try {
            socket = new Socket();
            this.socket = socket;
            if (Thread.currentThread() != thread) {
                // disconnected before the socket existed
                throw new IOException("Connection cancelled");
            }
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();
            socket.setKeepAlive(true);
            // the server and our pings keep a working connection busy, see XpraClient.PING_INTERVAL_MS
            socket.setSoTimeout(READ_TIMEOUT_MS);
            client.onConnect(new xpra.protocol.XpraSender(os));

            PacketReader reader = new PacketReader(is);
            logger.info("Start Xpra connection...");
            readPackets(reader);
            logger.info("Finnished Xpra connection!");
        } catch (IOException e) {
            client.onConnectionError(e);
            fireOnConnectionErrorEvent(e);
        } catch (RuntimeException e) {
            // ie: an invalid port, or data which cannot be read: not a reason to crash the app
            final IOException error = new IOException(e.getMessage(), e);
            client.onConnectionError(error);
            fireOnConnectionErrorEvent(error);
        } finally {
            if (socket != null) try {
                socket.close();
                if (client.getSender() != null) {
                    client.getSender().close();
                }
            } catch (Exception ignored) {
            }
            client.onDisconnect();
            fireOnDisconnectedEvent();
        }
    }

    /**
     * Processes packets until the connection is closed. The connection is considered
     * established once the Server accepted our hello, so if the Server disconnects before
     * that, its reason is reported as a connection error.
     */
    private void readPackets(PacketReader reader) throws IOException {
        boolean connected = false;
        while (!Thread.interrupted() && !client.isDisconnectedByServer()) {
            List<Object> dp = reader.readList();
            onPacketReceived(dp);
            if (!connected && client.isHandshakeComplete()) {
                connected = true;
                fireOnConnectedEvent();
            }
        }
        if (!connected) {
            final String reason = client.getDisconnectReason();
            throw new IOException(reason != null ? "The server refused the connection: " + reason
                : "The connection was closed during the handshake");
        }
    }

    public boolean isRunning() {
        final Thread t = thread;
        return t != null && t.isAlive();
    }

    @Override
    public boolean isAlive() {
        final Thread t = worker;
        return t != null && t.isAlive();
    }

}
