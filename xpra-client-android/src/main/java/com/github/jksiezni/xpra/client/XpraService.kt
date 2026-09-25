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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.os.Binder
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.github.jksiezni.xpra.MainActivity
import com.github.jksiezni.xpra.R
import com.github.jksiezni.xpra.config.ConnectionType
import com.github.jksiezni.xpra.config.ServerDetails
import com.github.jksiezni.xpra.ssh.SshUserInfoHandler
import com.jcraft.jsch.JSchException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import xpra.client.XpraConnector
import xpra.network.SshXpraConnector
import xpra.network.TcpXpraConnector
import java.io.IOException
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList


class XpraService : Service() {

    private val connectionObserver = ConnectionObserver()

    private lateinit var client: AndroidXpraClient
    private var connector: XpraConnector? = null
    private var serverDetails: ServerDetails? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    /** the handler of the connection made by the user, of which the reconnections reuse the passwords */
    private var userInfoHandler: SshUserInfoHandler? = null
    private var userDisconnected = false
    private var reconnecting = false
    private var reconnectAttempts = 0

    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate()")
        client = AndroidXpraClient(this)
        try {
            getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(networkCallback)
        } catch (e: RuntimeException) {
            Timber.w(e, "Cannot watch the network")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy()")
        try {
            getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(networkCallback)
        } catch (e: RuntimeException) {
            Timber.w(e, "Cannot stop watching the network")
        }
        mainHandler.removeCallbacksAndMessages(null)
        userDisconnected = true
        connector?.disconnect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand(): %s", intent)
        if (intent != null && ACTION_STOP == intent.action) {
            disconnect()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return LocalBinder()
    }

    @Throws(IOException::class)
    fun connect(serverDetails: ServerDetails, userInfoHandler: SshUserInfoHandler) {
        cancelReconnect()
        userDisconnected = false
        this.userInfoHandler = userInfoHandler
        startConnection(serverDetails, userInfoHandler, reconnect = false)
    }

    @Throws(IOException::class)
    private fun startConnection(serverDetails: ServerDetails, userInfoHandler: SshUserInfoHandler, reconnect: Boolean) {
        this.serverDetails = serverDetails
        connector = prepareConnector(serverDetails, userInfoHandler).apply {
            addListener(object : XpraConnector.ConnectionListener {
                private var connected = false
                private var ended = false

                override fun onConnected() {
                    connected = true
                    mainHandler.post {
                        reconnecting = false
                        reconnectAttempts = 0
                        if (reconnect) {
                            userInfoHandler.onConnected()
                        }
                        onConnect(serverDetails)
                        connectionObserver.onConnected(serverDetails)
                    }
                }

                override fun onDisconnected() {
                    end(null)
                }

                override fun onConnectionError(e: IOException) {
                    end(e)
                }

                /** connectors report an error, then the disconnection: handle the end once */
                private fun end(e: IOException?) {
                    if (ended) {
                        return
                    }
                    ended = true
                    val lost = (connected || reconnect) && isLost()
                    mainHandler.post { onConnectionEnded(serverDetails, e, lost) }
                }
            })
            connect()
        }
    }

    fun disconnect() {
        userDisconnected = true
        cancelReconnect()
        if (reconnecting && connector?.isRunning != true) {
            // between two attempts: nothing to close
            serverDetails?.let { onConnectionEnded(it, null, lost = false) }
        } else {
            connector?.disconnect()
        }
    }

    /**
     * Whether the connection was lost, rather than closed on purpose, by the user or by the
     * server (ie: when another client took over the session, or it was shut down). Servers
     * which did not hear from the client for a while also disconnect it, which is a loss.
     */
    private fun isLost(): Boolean {
        if (userDisconnected) {
            return false
        }
        // errors are reported before the client is reset, the disconnections after:
        val byServer = client.isDisconnectedByServer || client.wasLastDisconnectedByServer()
        if (!byServer) {
            return true
        }
        val reason = client.disconnectReason ?: client.lastDisconnectReason
        return reason?.contains("timeout", ignoreCase = true) == true
    }

    private fun onConnectionEnded(serverDetails: ServerDetails, e: IOException?, lost: Boolean) {
        if (lost && !userDisconnected && reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            if (!reconnecting) {
                reconnecting = true
                connectionObserver.onReconnecting(serverDetails)
            }
            updateNotification(serverDetails, getString(R.string.reconnecting_to, serverDetails.name))
            scheduleReconnect(serverDetails)
            return
        }
        reconnecting = false
        cancelReconnect()
        onDisconnect(serverDetails)
        if (e != null && !userDisconnected) {
            connectionObserver.onConnectionError(serverDetails, e)
        } else {
            connectionObserver.onDisconnected(serverDetails)
        }
    }

    private val reconnectRunnable = Runnable { reconnectNow() }

    /**
     * Tries again soon, then less and less often: the network may take a while to come back.
     */
    private fun scheduleReconnect(serverDetails: ServerDetails) {
        mainHandler.removeCallbacks(reconnectRunnable)
        val delay = RECONNECT_DELAYS_MS[minOf(reconnectAttempts, RECONNECT_DELAYS_MS.size - 1)]
        Timber.i("Reconnecting to %s in %d ms", serverDetails.name, delay)
        mainHandler.postDelayed(reconnectRunnable, delay)
    }

    private fun reconnectNow() {
        val server = serverDetails ?: return
        val handler = userInfoHandler ?: return
        if (!reconnecting || userDisconnected) {
            return
        }
        if (connector?.isRunning == true) {
            // the previous attempt is still ending
            scheduleReconnect(server)
            return
        }
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectAttempts++
        try {
            startConnection(server, handler.forReconnecting(this), reconnect = true)
        } catch (e: IOException) {
            Timber.w(e, "Cannot reconnect")
            onConnectionEnded(server, e, lost = true)
        }
    }

    private fun cancelReconnect() {
        mainHandler.removeCallbacks(reconnectRunnable)
        reconnectAttempts = 0
    }

    /**
     * Retries at once when the network comes back, ie: after switching from Wi-Fi to mobile data.
     */
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            mainHandler.post {
                if (reconnecting) {
                    reconnectNow()
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun prepareConnector(c: ServerDetails, userInfoHandler: SshUserInfoHandler): XpraConnector {
        // Xpra servers require a user name, even for connections without authentication
        client.setUsername(c.username?.takeIf { it.isNotBlank() } ?: DEFAULT_USERNAME)
        client.applySettings(c)
        return when (c.type) {
            ConnectionType.TCP -> TcpXpraConnector(client, c.host, c.port)
            ConnectionType.SSH -> {
                SshXpraConnector(client, c.host, c.username, c.port, userInfoHandler).apply {
                    setupSSHConnector(this, c)
                }
            }
        }
    }

    @Throws(IOException::class)
    private fun setupSSHConnector(connector: SshXpraConnector, c: ServerDetails) {
        val knownHosts = getFileStreamPath("known_hosts")
        try {
            if (knownHosts.isFile || knownHosts.createNewFile()) {
                connector.jsch.setKnownHosts(knownHosts.absolutePath)
            }
            if (c.sshPrivateKeyFile != null) {
                connector.jsch.addIdentity(c.sshPrivateKeyFile)
            }
            if (c.displayId >= 0) {
                connector.setDisplay(c.displayId)
            }
        } catch (e: JSchException) {
            throw IOException(e)
        }
    }

    private fun onConnect(serverDetails: ServerDetails) {
        startService(Intent(this, XpraService::class.java))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setupNotificationChannel()
        }
        ServiceCompat.startForeground(this, 1,
            buildNotification(getString(R.string.connected_to, serverDetails.name)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    /**
     * While reconnecting, the service stays in the foreground, which keeps the app running.
     */
    private fun updateNotification(serverDetails: ServerDetails, text: String) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        try {
            nm.notify(1, buildNotification(text))
        } catch (e: SecurityException) {
            Timber.w(e, "Cannot update the notification of %s", serverDetails.name)
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val stopIntent = Intent(this, XpraService::class.java)
        stopIntent.action = ACTION_STOP
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setLargeIcon(BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 2, mainIntent, PENDING_INTENT_FLAGS))
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.disconnect),
                        PendingIntent.getService(this, 1, stopIntent, PENDING_INTENT_FLAGS))
                .build()
    }

    private fun onDisconnect(serverDetails: ServerDetails) {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupNotificationChannel() {
        val name = getString(R.string.channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        channel.setShowBadge(false)
        val nm = getSystemService(NotificationManager::class.java)
        Objects.requireNonNull(nm).createNotificationChannel(channel)
    }

    private inner class LocalBinder : Binder(), XpraAPI {

        override fun getXpraClient(): AndroidXpraClient {
            return this@XpraService.client
        }

        override fun connect(serverDetails: ServerDetails, userInfoHandler: SshUserInfoHandler) {
            this@XpraService.connect(serverDetails, userInfoHandler)
        }

        override fun disconnect() {
            this@XpraService.disconnect()
        }

        override fun getConnectionDetails(): ServerDetails? {
            return serverDetails
        }

        override fun isConnected(): Boolean {
            return connector?.isRunning ?: false
        }

        override fun isReconnecting(): Boolean {
            return reconnecting
        }

        override fun registerConnectionListener(listener: ConnectionEventListener) {
            connectionObserver.registerObserver(listener)
        }

        override fun unregisterConnectionListener(listener: ConnectionEventListener) {
            connectionObserver.unregisterObserver(listener)
        }

    }

    private class ConnectionObserver : ConnectionEventListener {
        private val listeners = CopyOnWriteArrayList<ConnectionEventListener>()
        private val mainScope = CoroutineScope(Dispatchers.Main)

        fun registerObserver(listener: ConnectionEventListener) {
            listeners.add(listener)
        }

        fun unregisterObserver(listener: ConnectionEventListener) {
            listeners.remove(listener)
        }

        override fun onConnected(serverDetails: ServerDetails) {
            mainScope.launch {
                listeners.forEach { it.onConnected(serverDetails) }
            }
        }

        override fun onDisconnected(serverDetails: ServerDetails) {
            mainScope.launch {
                listeners.forEach { it.onDisconnected(serverDetails) }
            }
        }

        override fun onConnectionError(serverDetails: ServerDetails, e: IOException) {
            mainScope.launch {
                listeners.forEach { it.onConnectionError(serverDetails, e) }
            }
        }

        override fun onReconnecting(serverDetails: ServerDetails) {
            mainScope.launch {
                listeners.forEach { it.onReconnecting(serverDetails) }
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "service_channel"
        private const val ACTION_STOP = "action_stop"
        private const val DEFAULT_USERNAME = "android"
        /** how long to wait before each attempt to restore a lost connection */
        private val RECONNECT_DELAYS_MS = longArrayOf(1000, 2000, 4000, 8000, 15000, 30000)
        /** about 10 minutes of attempts */
        private const val MAX_RECONNECT_ATTEMPTS = 25
        private const val PENDING_INTENT_FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}