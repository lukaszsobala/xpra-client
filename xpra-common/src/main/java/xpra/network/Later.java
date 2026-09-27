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
 * Runs an action on a thread of its own, ie: closing a connection, which may use the network
 * and so cannot run on Android's main thread.
 */
final class Later {

    private Later() {
    }

    static void run(Runnable action, long delayMs) {
        final Thread thread = new Thread(() -> {
            if (delayMs > 0) try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                return;
            }
            action.run();
        }, "XpraClose");
        thread.setDaemon(true);
        thread.start();
    }
}
