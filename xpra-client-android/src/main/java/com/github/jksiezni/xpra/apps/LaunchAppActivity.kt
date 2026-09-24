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

package com.github.jksiezni.xpra.apps

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.github.jksiezni.xpra.ConnectXpraActivity
import com.github.jksiezni.xpra.R
import com.github.jksiezni.xpra.client.AndroidXpraWindow
import com.github.jksiezni.xpra.client.ConnectionEventListener
import com.github.jksiezni.xpra.client.ServiceBinderFragment
import com.github.jksiezni.xpra.client.XpraAPI
import com.github.jksiezni.xpra.client.XpraEventListener
import com.github.jksiezni.xpra.config.ConfigDatabase
import com.github.jksiezni.xpra.config.ServerDetails
import com.github.jksiezni.xpra.databinding.ActivityConnectBinding
import com.github.jksiezni.xpra.view.Intents
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import xpra.client.ServerApp
import java.io.IOException

/**
 * Starts an application of a server, from a home screen icon: connects to the server when
 * needed, then shows the window of the application, starting it if it does not run yet.
 */
class LaunchAppActivity : AppCompatActivity(), ConnectionEventListener, XpraEventListener {

    private val serviceBinderFragment by lazy { ServiceBinderFragment.obtain(this) }
    private val disposables = CompositeDisposable()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var binding: ActivityConnectBinding

    private var api: XpraAPI? = null
    private var serverId = -1
    private lateinit var appName: String
    private lateinit var command: String
    private var wmClass: String? = null

    /** Connects once the other server is disconnected. */
    private var connectWhenDisconnected = false
    /** The connection screen is shown, which reports the errors. */
    private var connecting = false
    private var waitingForWindow = false
    private var done = false

    private val connectLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        connecting = false
        val xpra = api
        if (result.resultCode == Activity.RESULT_OK && xpra != null) {
            launch(xpra)
        } else {
            finish()
        }
    }

    private val windowTimeout = Runnable {
        binding.connectProgressBar.visibility = View.GONE
        binding.connectionLabel.text = getString(R.string.app_no_window, appName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        serverId = intent.getIntExtra(AppShortcuts.EXTRA_SERVER_ID, -1)
        val cmd = intent.getStringExtra(AppShortcuts.EXTRA_COMMAND)
        if (AppShortcuts.ACTION_LAUNCH_APP != intent.action || serverId < 0 || cmd.isNullOrBlank()) {
            finish()
            return
        }
        command = cmd
        appName = intent.getStringExtra(AppShortcuts.EXTRA_NAME) ?: ServerApp.cleanCommand(cmd)
        wmClass = intent.getStringExtra(AppShortcuts.EXTRA_WM_CLASS)
        title = appName
        binding.connectionLabel.text = getString(R.string.starting_app, appName)

        serviceBinderFragment.whenXpraAvailable { xpra ->
            if (isFinishing) {
                return@whenXpraAvailable
            }
            api = xpra
            xpra.registerConnectionListener(this)
            val connected = xpra.connectionDetails
            when {
                xpra.isConnected && connected?.id == serverId -> launch(xpra)
                xpra.isConnected && connected != null -> askToSwitch(xpra, connected)
                else -> connect()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
        handler.removeCallbacks(windowTimeout)
        api?.let {
            it.unregisterConnectionListener(this)
            it.xpraClient.removeEventListener(this)
        }
    }

    private fun connect(attempt: Int = 0) {
        if (isFinishing) {
            return
        }
        connecting = true
        if (api?.isConnected == true && attempt < RECONNECT_ATTEMPTS) {
            // the previous connection is still closing:
            handler.postDelayed({ connect(attempt + 1) }, RECONNECT_DELAY_MS)
            return
        }
        val intent = Intent(this, ConnectXpraActivity::class.java)
        intent.putExtra(ConnectXpraActivity.EXTRA_CONNECTION_ID, serverId)
        connectLauncher.launch(intent)
    }

    /**
     * A single connection is supported: offer to leave the other server.
     */
    private fun askToSwitch(xpra: XpraAPI, connected: ServerDetails) {
        ConfigDatabase.getInstance().configs.getById(serverId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ server ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.switch_server_title)
                    .setMessage(getString(R.string.switch_server_message, connected.name, server.name))
                    .setPositiveButton(R.string.switch_server) { _, _ ->
                        connectWhenDisconnected = true
                        xpra.disconnect()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .show()
            }, { throwable ->
                Timber.e(throwable)
                Toast.makeText(this, R.string.server_removed, Toast.LENGTH_LONG).show()
                finish()
            })
            .addTo(disposables)
    }

    private fun launch(xpra: XpraAPI) {
        binding.connectProgressBar.visibility = View.VISIBLE
        binding.connectionLabel.text = getString(R.string.starting_app, appName)
        // the windows of the applications which run already come first:
        xpra.xpraClient.whenStartupComplete {
            if (!isFinishing && !done) {
                showOrStart(xpra)
            }
        }
    }

    private fun showOrStart(xpra: XpraAPI) {
        val client = xpra.xpraClient
        val running = client.windows.filterIsInstance<AndroidXpraWindow>().firstOrNull {
            isAppWindow(it) && ServerApp.matchesWindow(command, wmClass, it.windowClasses)
        }
        if (running != null) {
            showWindow(running.id)
            return
        }
        if (!client.canStartCommands()) {
            binding.connectProgressBar.visibility = View.GONE
            binding.connectionLabel.setText(R.string.cannot_start_apps)
            return
        }
        waitingForWindow = true
        client.addEventListener(this)
        client.startCommand(appName, command)
        handler.postDelayed(windowTimeout, WINDOW_TIMEOUT_MS)
    }

    private fun isAppWindow(window: AndroidXpraWindow) = !window.hasParent() && !window.isOverrideRedirect

    private fun showWindow(windowId: Int) {
        done = true
        // each window has its own task, like an app: see the manifest
        startActivity(Intents.createXpraIntent(this, windowId))
        finish()
    }

    override fun onWindowCreated(window: AndroidXpraWindow) {
        // called by the connection thread
        if (isAppWindow(window)) {
            handler.post {
                if (waitingForWindow && !done && !isFinishing) {
                    showWindow(window.id)
                }
            }
        }
    }

    override fun onWindowLost(window: AndroidXpraWindow) {
    }

    override fun onConnected(serverDetails: ServerDetails) {
    }

    override fun onDisconnected(serverDetails: ServerDetails) {
        if (connectWhenDisconnected) {
            connectWhenDisconnected = false
            connect()
        } else if (!done && !connecting) {
            finish()
        }
    }

    override fun onConnectionError(serverDetails: ServerDetails, e: IOException) {
        onDisconnected(serverDetails)
    }

    fun onCancel(@Suppress("UNUSED_PARAMETER") view: View) {
        finish()
    }

    private companion object {
        const val WINDOW_TIMEOUT_MS = 30_000L
        const val RECONNECT_DELAY_MS = 250L
        const val RECONNECT_ATTEMPTS = 20
    }
}
