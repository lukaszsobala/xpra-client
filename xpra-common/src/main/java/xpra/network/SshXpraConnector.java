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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xpra.client.XpraClient;
import xpra.client.XpraConnector;
import xpra.protocol.packets.Disconnect;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

/**
 * An SSH connector to Xpra Server.
 */
public class SshXpraConnector extends XpraConnector implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SshXpraConnector.class);

    /** without it, connecting to an unreachable server waits for minutes, ie: while reconnecting */
    private static final int CONNECT_TIMEOUT_MS = 15_000;

    private static final String[] REMOTE_XPRA = {
        "xpra", "$XDG_RUNTIME_DIR/xpra/run-xpra", "/usr/local/bin/xpra", "~/.xpra/run-xpra"
    };

    private final JSch jsch = new JSch();

    private final UserInfo userInfo;
    private final String username;
    private final String host;
    private final int port;

    /**
     * The display to connect to, or -1 to let the server pick its only session.
     */
    private int display = -1;

    private volatile Thread thread;
    /** the thread of the connection, until it ends: see {@link #isAlive()} */
    private volatile Thread worker;
    private volatile Session session;

    public SshXpraConnector(XpraClient client, String host) {
        this(client, host, null);
    }

    public SshXpraConnector(XpraClient client, String host, String username) {
        this(client, host, username, 22, null);
    }

    public SshXpraConnector(XpraClient client, String host, String username, int port, UserInfo userInfo) {
        super(client);
        this.host = host;
        this.username = username;
        this.port = port;
        this.userInfo = userInfo;
        JSch.setConfig("compression_level", "0");
    }

    @Override
    public synchronized boolean connect() {
        if (thread != null) {
            return false;
        }
        try {
            session = jsch.getSession(username, host, port);
            session.setUserInfo(userInfo);
            //disableStrictHostKeyChecking();
        } catch (JSchException e) {
            // the listeners wait for the end of the connection, like when it fails later
            final IOException error = new IOException(e);
            client.onConnectionError(error);
            fireOnConnectionErrorEvent(error);
            fireOnDisconnectedEvent();
            return false;
        }
        thread = new Thread(this, "XpraSshConnection");
        worker = thread;
        thread.start();
        return true;
    }

    /**
     * This setting will cause JSCH to automatically add all target servers'
     * entry to the known_hosts file
     */
    void disableStrictHostKeyChecking() {
        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
    }

    @Override
    public synchronized void disconnect() {
        final Thread t = thread;
        if (t != null) {
            thread = null;
            if (disconnectCleanly()) {
                // a server which cannot be reached any more never closes the connection
                Later.run(this::closeSession, TcpXpraConnector.DISCONNECT_GRACE_MS);
            } else {
                // still connecting, or waiting for the user to type a password
                t.interrupt();
                Later.run(this::closeSession, 0);
            }
        }
    }

    private void closeSession() {
        final Session s = session;
        if (s != null) {
            s.disconnect();
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
    public boolean isRunning() {
        final Thread t = thread;
        return t != null && t.isAlive();
    }

    @Override
    public boolean isAlive() {
        final Thread t = worker;
        return t != null && t.isAlive();
    }

    @Override
    public void run() {
        // the remote xpra command reports its errors on stderr:
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final Session session = this.session;
        try {
            if (Thread.currentThread() != thread) {
                throw new IOException("Connection cancelled");
            }
            session.setServerAliveInterval(1000);
            session.setServerAliveCountMax(15);
            logger.debug("Keep-alive interval={}, maxAliveCount={}", session.getServerAliveInterval(), session.getServerAliveCountMax());
            session.connect(CONNECT_TIMEOUT_MS);
            if (Thread.currentThread() != thread) {
                // disconnected while the session was being set up
                throw new IOException("Connection cancelled");
            }
            final Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(getProxyCommand(display));
            ((ChannelExec) channel).setErrStream(stderr, true);
            channel.connect();

            final InputStream in = channel.getInputStream();
            client.onConnect(new xpra.protocol.XpraSender(channel.getOutputStream()));
            PacketReader reader = new PacketReader(in);
            logger.info("Start Xpra connection...");
            readPackets(reader);
        } catch (JSchException e) {
            client.onConnectionError(new IOException(e));
            fireOnConnectionErrorEvent(new IOException(e));
        } catch (IOException e) {
            final IOException error = withRemoteError(e, stderr);
            client.onConnectionError(error);
            fireOnConnectionErrorEvent(error);
        } catch (RuntimeException e) {
            // ie: data which cannot be read: not a reason to crash the app
            final IOException error = new IOException(e.getMessage(), e);
            client.onConnectionError(error);
            fireOnConnectionErrorEvent(error);
        } finally {
            logger.info("Finnished Xpra connection!");
            if (client.getSender() != null) try {
                client.getSender().close();
            } catch (IOException ignore) {
            }
            session.disconnect();
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

    public JSch getJsch() {
        return jsch;
    }

    public void setDisplay(int displayId) {
        this.display = displayId;
    }

    /**
     * Adds the error printed by the remote command, if the connection failed before the handshake completed.
     */
    private IOException withRemoteError(IOException e, ByteArrayOutputStream stderr) {
        if (client.isHandshakeComplete()) {
            return e;
        }
        try {
            // give the SSH session a moment to deliver the rest of stderr
            Thread.sleep(200);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        final String output;
        synchronized (stderr) {
            output = new String(stderr.toByteArray(), StandardCharsets.UTF_8).trim();
        }
        if (output.isEmpty()) {
            return e;
        }
        // the last lines are the most relevant ones:
        final String[] lines = output.split("\\r?\\n");
        final StringBuilder message = new StringBuilder();
        for (int i = Math.max(0, lines.length - 3); i < lines.length; ++i) {
            if (message.length() > 0) {
                message.append('\n');
            }
            message.append(lines[i].trim());
        }
        return new IOException(message.toString(), e);
    }

    /**
     * Builds the remote command that connects the SSH channel to an Xpra session, trying the usual
     * locations of the xpra command like Xpra's own client does (see {@code xpra/net/ssh/exec_client.py}).
     */
    static String getProxyCommand(int display) {
        final String args = display >= 0 ? " _proxy :" + display : " _proxy";
        final StringBuilder cmd = new StringBuilder();
        for (String xpra : REMOTE_XPRA) {
            cmd.append(cmd.length() == 0 ? "if " : "elif ");
            if ("xpra".equals(xpra)) {
                cmd.append("command -v xpra > /dev/null 2>&1");
            } else {
                cmd.append("[ -x ").append(xpra).append(" ]");
            }
            cmd.append("; then ").append(xpra).append(args).append("; ");
        }
        cmd.append("else echo \"no xpra command found\"; exit 1; fi");
        return "sh -c '" + cmd + "'";
    }

}
