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

package com.github.jksiezni.xpra.client

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * From Android 17, for apps which target it, the local network needs a permission of its
 * own, ACCESS_LOCAL_NETWORK ("Nearby devices"): without it, connections to the servers at
 * home or at work time out.
 */
object LocalNetwork {

    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** Android 17 */
    private const val FIRST_SDK = 37

    fun isAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < FIRST_SDK ||
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether [host] is on a local network: a private or link-local address, or an mDNS
     * name. It resolves the name, so it must not run on the main thread.
     */
    fun isLocal(host: String): Boolean {
        if (host.endsWith(".local", ignoreCase = true)) {
            return true
        }
        val addresses = try {
            InetAddress.getAllByName(host)
        } catch (e: UnknownHostException) {
            // the connection says so
            return false
        }
        return addresses.any { isLocal(it) }
    }

    fun isLocal(address: InetAddress): Boolean = when (address) {
        // 10/8, 172.16/12, 192.168/16, 169.254/16, and 100.64/10 of the VPNs (ie: Tailscale)
        is Inet4Address -> address.isSiteLocalAddress || address.isLinkLocalAddress ||
            ((address.address[0].toInt() and 0xff) == 100 && (address.address[1].toInt() and 0xc0) == 64)
        // fe80::/10, and the unique local addresses fc00::/7
        is Inet6Address -> address.isLinkLocalAddress || address.isSiteLocalAddress ||
            (address.address[0].toInt() and 0xfe) == 0xfc
        else -> false
    }
}
