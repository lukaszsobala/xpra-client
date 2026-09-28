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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshXpraConnectorTest {

    private static final String LIVE_100 = "Found the following xpra sessions:\n"
        + "/run/user/1000/xpra:\n"
        + "\tLIVE session at :100\n"
        + "\tDEAD session at :101\n"
        + "--x11-sockets--\n"
        + "X0\nX100\nX101\n";

    @Test
    public void connectsToTheRunningServer() {
        final SshXpraConnector.Sessions sessions = SshXpraConnector.Sessions.parse(LIVE_100);
        assertTrue(sessions.known);
        assertEquals(-1, sessions.displayToStart(-1));
        assertEquals(-1, sessions.displayToStart(100));
    }

    @Test
    public void startsOnTheChosenDisplay() {
        final SshXpraConnector.Sessions sessions = SshXpraConnector.Sessions.parse(LIVE_100);
        assertEquals(101, sessions.displayToStart(101));
        assertEquals(200, sessions.displayToStart(200));
    }

    @Test
    public void startsOnAFreeDisplay() {
        final SshXpraConnector.Sessions sessions = SshXpraConnector.Sessions.parse(
            "Found the following xpra sessions:\n/run/user/1000/xpra:\n\tDEAD session at :100\n"
                + "--x11-sockets--\nX0\nX101\n");
        // neither the dead session, nor another X server:
        assertEquals(102, sessions.displayToStart(-1));
    }

    @Test
    public void startsWhenThereIsNoSession() {
        final SshXpraConnector.Sessions sessions = SshXpraConnector.Sessions.parse("No xpra sessions found\n--x11-sockets--\n");
        assertTrue(sessions.known);
        assertEquals(SshXpraConnector.FIRST_DISPLAY, sessions.displayToStart(-1));
        assertEquals(7, sessions.displayToStart(7));
    }

    @Test
    public void neverStartsWhenTheSessionsAreUnknown() {
        // ie: xpra failed with a Python error
        final SshXpraConnector.Sessions sessions = SshXpraConnector.Sessions.parse(
            "Traceback (most recent call last):\nImportError: cannot import name\n--x11-sockets--\n");
        assertFalse(sessions.known);
        assertEquals(-1, sessions.displayToStart(-1));
        assertEquals(-1, sessions.displayToStart(100));
    }

    @Test
    public void quotesTheStartCommand() {
        assertEquals("'xterm'", SshXpraConnector.shellQuote("xterm"));
        assertEquals("'xterm -T '\\''my term'\\'''", SshXpraConnector.shellQuote("xterm -T 'my term'"));
        final String command = SshXpraConnector.getStartCommand(100, " xterm ");
        assertTrue(command, command.startsWith("sh -c '"));
        assertTrue(command, command.contains("_proxy_start :100 '\\''--start=xterm'\\''"));
    }

    @Test
    public void reportsAMissingXpraOnStderr() {
        assertTrue(SshXpraConnector.getProxyCommand(100).contains("echo \"no xpra command found\" >&2; exit 127"));
    }
}
