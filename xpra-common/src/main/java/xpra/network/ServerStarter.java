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

/**
 * Asks the user whether to start an Xpra server over SSH, when none is running.
 */
public interface ServerStarter {

    /**
     * Called from the connection's thread, which waits for the answer.
     *
     * @param display the display the server would use, ie: 100 for ":100"
     * @return the command to start in the new server, empty for none, or null to not start one
     */
    String askToStart(int display) throws InterruptedException;
}
