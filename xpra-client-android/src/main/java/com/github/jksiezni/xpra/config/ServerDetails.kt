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

package com.github.jksiezni.xpra.config

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import xpra.protocol.PictureEncoding
import java.io.Serializable
import java.util.*

@Entity
class ServerDetails : Serializable {

    @PrimaryKey(autoGenerate = true)
    var id: Int = 0

    var name: String? = null

    var type: ConnectionType = ConnectionType.TCP

    var host: String? = null

    var port: Int = 10000

    var displayId: Int = -1

    var username: String? = null

    var sshPrivateKeyFile: String? = null

    var pictureEncoding: PictureEncoding = PictureEncoding.jpeg

    /**
     * How many screen pixels are used for one pixel of the remote windows, in percent,
     * or [SCALE_AUTOMATIC] to follow the screen density.
     */
    @ColumnInfo(defaultValue = "0")
    var scalePercent: Int = SCALE_AUTOMATIC

    /**
     * Whether text copied on the device can be pasted on the server, and the other way round.
     */
    @ColumnInfo(defaultValue = "1")
    var clipboardSharing: Boolean = true

    /**
     * How long to wait for the window of an app started from the phone, in seconds, or
     * [WAIT_FOREVER]: slow servers may take longer to start big apps.
     */
    @ColumnInfo(defaultValue = "30")
    var appWindowTimeout: Int = DEFAULT_APP_WINDOW_TIMEOUT

    /**
     * Whether the server may send video streams, decoded by the device, for what changes a lot.
     */
    @ColumnInfo(defaultValue = "1")
    var videoDecoding: Boolean = true

    val url: String
        get() {
            val builder = StringBuilder(type.toString().lowercase(Locale.getDefault()))
            builder.append("://")
            if (type == ConnectionType.SSH) {
                builder.append(username)
                builder.append('@')
            }
            builder.append(host)
            builder.append(':')
            builder.append(port)
            return builder.toString()
        }

    override fun toString(): String {
        return name.orEmpty()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as ServerDetails
        return id == that.id && port == that.port && displayId == that.displayId &&
                name == that.name && type == that.type &&
                host == that.host &&
                username == that.username &&
                sshPrivateKeyFile == that.sshPrivateKeyFile && pictureEncoding == that.pictureEncoding &&
                scalePercent == that.scalePercent && clipboardSharing == that.clipboardSharing &&
                appWindowTimeout == that.appWindowTimeout && videoDecoding == that.videoDecoding
    }

    override fun hashCode(): Int {
        return Objects.hash(id, name, type, host, port, displayId, username, sshPrivateKeyFile, pictureEncoding, scalePercent, clipboardSharing,
            appWindowTimeout, videoDecoding)
    }

    companion object {
        const val SCALE_AUTOMATIC = 0
        const val DEFAULT_APP_WINDOW_TIMEOUT = 30
        const val WAIT_FOREVER = 0
    }
}
