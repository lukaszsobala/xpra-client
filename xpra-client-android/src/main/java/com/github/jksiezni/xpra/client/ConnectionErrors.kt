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
import com.github.jksiezni.xpra.R
import com.github.jksiezni.xpra.config.ServerDetails
import com.jcraft.jsch.JSchException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Explains why a connection failed, rather than showing the message of the exception,
 * ie: "com.jcraft.jsch.JSchException: Auth fail".
 */
object ConnectionErrors {

    fun describe(context: Context, server: ServerDetails, e: Throwable): String {
        val causes = generateSequence(e) { it.cause }.take(10).toList()
        val jsch = causes.filterIsInstance<JSchException>().firstOrNull()?.message.orEmpty()
        return when {
            causes.any { it is UnknownHostException } ->
                context.getString(R.string.error_unknown_host, server.host)
            causes.any { it is ConnectException } ->
                context.getString(R.string.error_refused, server.host, server.port)
            causes.any { it is SocketTimeoutException || it is NoRouteToHostException } ||
                jsch.contains("timeout", ignoreCase = true) ->
                context.getString(R.string.error_timeout, server.host)
            jsch.startsWith("Auth", ignoreCase = true) ->
                context.getString(R.string.error_ssh_auth)
            jsch.contains("HostKey", ignoreCase = true) ->
                context.getString(R.string.error_ssh_host_key)
            else -> readable(e) ?: context.getString(R.string.error_connection)
        }
    }

    /** the message without the names of the exceptions, which wrap each other */
    private fun readable(e: Throwable): String? =
        e.message?.replace(Regex("""^(?:(?:[\w$]+\.)+[\w$]*(?:Exception|Error):\s*)+"""), "")?.takeIf { it.isNotBlank() }
}
