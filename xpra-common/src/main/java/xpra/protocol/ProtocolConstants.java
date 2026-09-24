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

package xpra.protocol;

public class ProtocolConstants {

	/**
	 * The Xpra protocol level implemented by this client (rencodeplus packet encoding,
	 * nested capabilities), announced as the "version" capability.
	 * Servers older than v3.0 reject it; the server only uses it for logging otherwise.
	 */
	public static final String VERSION = "6.0";

	/**
	 * The oldest server version this client works with, announced as the "protocol-version"
	 * capability: servers only accept the "rencodeplus" packet encoding since v5.0.
	 */
	public static final int[] MIN_SERVER_VERSION = {5, 0};
}
