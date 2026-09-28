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
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** printed on stderr by the remote commands when none of {@link #REMOTE_XPRA} exists */
    static final String XPRA_NOT_FOUND = "no xpra command found";

    /** separates the sessions listed by xpra from the X displays in use, see {@link #getListCommand()} */
    private static final String X11_MARKER = "--x11-sockets--";

    /** ie: "LIVE session at :100", as printed by "xpra list" */
    private static final Pattern SESSION = Pattern.compile("\\b(LIVE|DEAD|UNKNOWN)\\b.*?:(\\d+)\\b");

    /** a socket of /tmp/.X11-unix, ie: "X0" for display :0 */
    private static final Pattern X11_SOCKET = Pattern.compile("^X(\\d+)$");

    /** the displays used for the servers started by this client, like Xpra's own examples */
    static final int FIRST_DISPLAY = 100;

    private final JSch jsch = new JSch();

    private final UserInfo userInfo;
    private final String username;
    private final String host;
    private final int port;

    /**
     * The display to connect to, or -1 to let the server pick its only session.
     */
    private int display = -1;

    /** asks whether to start a server when none is running, or null to never start one */
    private volatile ServerStarter serverStarter;
    /** the display of the server this connection started, or -1 */
    private volatile int startedDisplay = -1;

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
            final String command = chooseRemoteCommand(session);
            final Channel channel = session.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
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
     * Checks which Xpra servers are running before connecting, to offer starting one when there
     * is none: for the connections made by the user, never when reconnecting, which would start
     * again a server the user stopped.
     */
    public void setServerStarter(ServerStarter starter) {
        this.serverStarter = starter;
    }

    /**
     * @return the display of the server started by this connection, or -1
     */
    public int getStartedDisplay() {
        return startedDisplay;
    }

    /**
     * Connects to the running server, or starts one if the user wants to.
     */
    private String chooseRemoteCommand(Session session) throws JSchException, IOException {
        final ServerStarter starter = serverStarter;
        if (starter == null) {
            return getProxyCommand(display);
        }
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final String output = runCommand(session, getListCommand(), stderr);
        if (output.contains(XPRA_NOT_FOUND) || stderr.toString(StandardCharsets.UTF_8.name()).contains(XPRA_NOT_FOUND)) {
            throw new XpraNotFoundException();
        }
        final Sessions sessions = Sessions.parse(output);
        final int toStart = sessions.displayToStart(display);
        if (toStart < 0) {
            return getProxyCommand(display);
        }
        final String startCommand;
        try {
            startCommand = starter.askToStart(toStart);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Connection cancelled", e);
        }
        if (Thread.currentThread() != thread) {
            throw new IOException("Connection cancelled");
        }
        if (startCommand == null) {
            throw new NoServerException(toStart);
        }
        logger.info("Starting an Xpra server on :{}", toStart);
        startedDisplay = toStart;
        return getStartCommand(toStart, startCommand);
    }

    /**
     * Runs a command on the server, and returns what it printed on stdout.
     */
    private static String runCommand(Session session, String command, ByteArrayOutputStream stderr)
            throws JSchException, IOException {
        final ChannelExec channel = (ChannelExec) session.openChannel("exec");
        try {
            channel.setCommand(command);
            channel.setErrStream(stderr, true);
            final InputStream in = channel.getInputStream();
            channel.connect(CONNECT_TIMEOUT_MS);
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        } finally {
            channel.disconnect();
        }
    }

    /**
     * The Xpra sessions of the server, from {@link #getListCommand()}.
     */
    static final class Sessions {
        /** whether the output came from "xpra list": otherwise nothing is known */
        final boolean known;
        final Set<Integer> live = new TreeSet<>();
        /** the displays not to start a server on: of the other sessions, or of other X servers */
        final Set<Integer> used = new TreeSet<>();

        private Sessions(boolean known) {
            this.known = known;
        }

        static Sessions parse(String output) {
            final String[] parts = output.split(X11_MARKER, 2);
            final String list = parts[0];
            final Matcher m = SESSION.matcher(list);
            boolean found = false;
            final Sessions sessions = new Sessions(true);
            while (m.find()) {
                found = true;
                final int d = Integer.parseInt(m.group(2));
                sessions.used.add(d);
                if ("LIVE".equals(m.group(1))) {
                    sessions.live.add(d);
                }
            }
            // ie: "Found the following xpra sessions:" or "No xpra sessions found"
            if (!found && !list.toLowerCase(Locale.ROOT).contains("xpra sessions")) {
                return new Sessions(false);
            }
            if (parts.length > 1) {
                for (String line : parts[1].split("\\r?\\n")) {
                    final Matcher x = X11_SOCKET.matcher(line.trim());
                    if (x.matches()) {
                        sessions.used.add(Integer.parseInt(x.group(1)));
                    }
                }
            }
            return sessions;
        }

        /**
         * @param display the display the user chose, or -1 for any
         * @return the display to start a server on, or -1 to connect as usual: when a server is
         * running, or when it cannot be known (then connecting reports the error)
         */
        int displayToStart(int display) {
            if (!known) {
                return -1;
            }
            if (display >= 0) {
                return live.contains(display) ? -1 : display;
            }
            if (!live.isEmpty()) {
                return -1;
            }
            int d = FIRST_DISPLAY;
            while (used.contains(d)) {
                ++d;
            }
            return d;
        }
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
        if (output.contains(XPRA_NOT_FOUND)) {
            final IOException notFound = new XpraNotFoundException();
            notFound.initCause(e);
            return notFound;
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
        return sh(xpraScript(display >= 0 ? " _proxy :" + display : " _proxy"));
    }

    /**
     * Starts a server on the display and connects to it, like "xpra start ssh://host/:100" does.
     *
     * @param startCommand the command to start in the server, or empty for none
     */
    static String getStartCommand(int display, String startCommand) {
        final String start = startCommand.trim();
        return sh(xpraScript(" _proxy_start :" + display + (start.isEmpty() ? "" : " " + shellQuote("--start=" + start))));
    }

    /**
     * Lists the Xpra sessions, then the X displays in use, which a new server must not take.
     */
    static String getListCommand() {
        return sh(xpraScript(" list 2>&1") + "; echo " + X11_MARKER + "; ls /tmp/.X11-unix 2>/dev/null");
    }

    /**
     * Runs the script with sh, whatever the login shell of the user (ie: fish or csh).
     */
    private static String sh(String script) {
        return "sh -c " + shellQuote(script);
    }

    /**
     * Runs xpra with the arguments, from the first location found.
     */
    private static String xpraScript(String args) {
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
        // on stderr, as stdout carries the packets:
        cmd.append("else echo \"" + XPRA_NOT_FOUND + "\" >&2; exit 127; fi");
        return cmd.toString();
    }

    /**
     * Quotes a word for sh, ie: a command typed by the user, which may contain quotes itself.
     */
    static String shellQuote(String word) {
        return "'" + word.replace("'", "'\\''") + "'";
    }

}
