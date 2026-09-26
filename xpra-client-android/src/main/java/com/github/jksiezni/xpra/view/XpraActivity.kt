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
package com.github.jksiezni.xpra.view

import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import com.github.jksiezni.xpra.R
import com.github.jksiezni.xpra.apps.AppShortcuts
import com.github.jksiezni.xpra.client.*
import com.github.jksiezni.xpra.client.AndroidXpraWindow.XpraWindowListener
import com.github.jksiezni.xpra.view.Intents.getWindowId
import com.github.jksiezni.xpra.view.Intents.isValidXpraActivityIntent
import com.github.jksiezni.xpra.config.ConfigDatabase
import com.github.jksiezni.xpra.config.ServerDetails
import io.reactivex.schedulers.Schedulers
import kotlin.math.roundToInt
import com.github.jksiezni.xpra.databinding.ActivityXpraBinding
import timber.log.Timber
import xpra.client.KeyboardInput
import xpra.client.ServerApp
import java.io.IOException

class XpraActivity : AppCompatActivity(), XpraEventListener, XpraWindowListener, ConnectionEventListener {

    private lateinit var binding: ActivityXpraBinding

    private lateinit var serviceBinderFragment: ServiceBinderFragment

    private var windowId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.i("onCreate(): %s", intent)
        serviceBinderFragment = ServiceBinderFragment.obtain(this)
        binding = ActivityXpraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge(this, binding.root, binding.toolbar) { imeVisible ->
            binding.extraKeys.visibility = if (imeVisible) View.VISIBLE else View.GONE
        }
        if (!isValidXpraActivityIntent(intent)) {
            finish()
            return
        }
        windowId = getWindowId(intent)
        setSupportActionBar(binding.toolbar)
        setupLandscape()
        binding.workspaceView.touchpadMode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_TOUCHPAD_MODE, false)

        serviceBinderFragment.whenXpraAvailable { api ->
            val rootWindow = api.xpraClient.getWindow(windowId)
            if (rootWindow == null && !api.isReconnecting) {
                Timber.w("Window with windowId=%d not found", windowId)
                finish()
                return@whenXpraAvailable
            }
            serverApps = { api.xpraClient.serverApps }
            api.registerConnectionListener(this)
            api.xpraClient.addEventListener(this)
            // the activity is created again when the device rotates:
            api.xpraClient.updateDesktopSize(resources.displayMetrics)
            if (rootWindow != null) {
                bindWindow(rootWindow)
            } else {
                // ie: opened from the recent apps while the connection is being restored
                onReconnecting(api.connectionDetails ?: ServerDetails())
            }
            setResult(RESULT_OK)
        }
    }

    /** the applications of the server, to name the windows whose title means nothing */
    private var serverApps: () -> List<ServerApp> = { emptyList() }

    /** the window shown, which is a new object after reconnecting */
    private var boundWindow: AndroidXpraWindow? = null

    /** while reconnecting, the windows which the server sends again are not new ones */
    @Volatile
    private var restoring = false

    private fun bindWindow(rootWindow: AndroidXpraWindow) {
        boundWindow?.removeWindowListener(this)
        boundWindow = rootWindow
        title = rootWindow.label(this, serverApps())
        updateTaskDescription(rootWindow)
        rootWindow.addWindowListener(this)
        binding.workspaceView.removeAllViews()
        restoreProxyViewHierarchy(rootWindow)
        val keyboardInput = KeyboardInput(rootWindow)
        binding.workspaceView.keyboardInput = keyboardInput
        binding.extraKeys.keyboardInput = keyboardInput
        binding.workspaceView.requestFocus()
    }

    override fun onReconnecting(serverDetails: ServerDetails) {
        restoring = true
        binding.reconnectingBanner.visibility = View.VISIBLE
    }

    /**
     * Gives the window again to the views once the connection is restored, or closes when the
     * window is gone.
     */
    private fun onReconnected(api: XpraAPI) {
        api.xpraClient.whenStartupComplete {
            if (isFinishing || isDestroyed) {
                return@whenStartupComplete
            }
            restoring = false
            binding.reconnectingBanner.visibility = View.GONE
            val rootWindow = api.xpraClient.getWindow(windowId)
            if (rootWindow == null) {
                finish()
            } else if (rootWindow !== boundWindow) {
                api.xpraClient.updateDesktopSize(resources.displayMetrics)
                bindWindow(rootWindow)
            }
        }
    }

    /**
     * Landscape screens are short: go full screen, with the system bars shown by a swipe from
     * the edge, and hide the toolbar behind a small handle.
     */
    private fun setupLandscape() {
        if (resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        binding.toolbarHandle.visibility = View.VISIBLE
        setToolbarShown(false)
        binding.toolbarHandle.setOnClickListener {
            setToolbarShown(binding.toolbar.visibility != View.VISIBLE)
        }
    }

    private fun setToolbarShown(shown: Boolean) {
        binding.toolbar.visibility = if (shown) View.VISIBLE else View.GONE
        binding.toolbarHandle.setIconResource(
            if (shown) R.drawable.ic_baseline_expand_less_24 else R.drawable.ic_baseline_expand_more_24)
        binding.toolbarHandle.contentDescription = getString(if (shown) R.string.hide_toolbar else R.string.show_toolbar)
    }

    private fun restoreProxyViewHierarchy(rootWindow: AndroidXpraWindow) {
        binding.workspaceView.addView(ProxyView(this, rootWindow))
        val list = mutableListOf<AndroidXpraWindow>()
        list.addAll(rootWindow.children)
        while (list.isNotEmpty()) {
            val child = list.removeAt(0)
            val proxyView = ProxyView(this, child)
            binding.workspaceView.addView(proxyView)
            child.addWindowListener(XpraWindowHandler(proxyView))
            list.addAll(child.children)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceBinderFragment.whenXpraAvailable { api ->
            boundWindow?.removeWindowListener(this)
            api.xpraClient.removeEventListener(this)
            api.unregisterConnectionListener(this)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // ie: back from the app where some text was copied
            sendClipboard()
        }
    }

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener { sendClipboard() }

    override fun onResume() {
        super.onResume()
        serviceBinderFragment.whenXpraAvailable { api -> api.xpraClient.activeWindowId = windowId }
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipChangedListener)
    }

    override fun onPause() {
        super.onPause()
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipChangedListener)
    }

    private fun sendClipboard() {
        serviceBinderFragment.whenXpraAvailable { api ->
            AndroidClipboard.sendToServer(this, api.xpraClient.clipboard)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Timber.i("onNewIntent(): %s", getIntent())
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.xpra_menu, menu)
        updateTouchpadItem(menu.findItem(R.id.action_touchpad))
        return true
    }

    /**
     * Shows the mode the button switches to.
     */
    private fun updateTouchpadItem(item: MenuItem?) {
        item ?: return
        if (binding.workspaceView.touchpadMode) {
            item.setIcon(R.drawable.ic_baseline_touch_app_24)
            item.setTitle(R.string.direct_touch_mode)
        } else {
            item.setIcon(R.drawable.ic_baseline_mouse_24)
            item.setTitle(R.string.touchpad_mode)
        }
    }

    private fun toggleTouchpadMode(item: MenuItem) {
        val touchpad = !binding.workspaceView.touchpadMode
        binding.workspaceView.touchpadMode = touchpad
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_TOUCHPAD_MODE, touchpad)
            .apply()
        updateTouchpadItem(item)
        Toast.makeText(this, if (touchpad) R.string.touchpad_mode_on else R.string.direct_touch_mode_on,
            Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_touchpad -> {
                toggleTouchpadMode(item)
                true
            }
            R.id.action_keyboard -> {
                toggleKeyboard(binding.workspaceView)
                true
            }
            R.id.action_add_to_home_screen -> {
                addToHomeScreen()
                true
            }
            R.id.action_close -> {
                serviceBinderFragment.whenXpraAvailable { api ->
                    val window = api.xpraClient.getWindow(windowId)
                    window?.close()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * The volume keys change the scale of the windows.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val step = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> 1
            KeyEvent.KEYCODE_VOLUME_DOWN -> -1
            else -> 0
        }
        if (step == 0) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            changeScale(step)
        }
        return true
    }

    private var scaleToast: Toast? = null

    private fun changeScale(step: Int) {
        serviceBinderFragment.whenXpraAvailable { api ->
            val client = api.xpraClient
            val current = (client.scale * 100).roundToInt()
            val percent = if (step > 0) SCALE_LEVELS.firstOrNull { it > current } else SCALE_LEVELS.lastOrNull { it < current }
            if (percent != null) {
                client.changeScale(percent / 100f, resources.displayMetrics)
                val workspace = binding.workspaceView
                for (i in 0 until workspace.childCount) {
                    val view = workspace.getChildAt(i) as? ProxyView ?: continue
                    view.window.resize(view.width, view.height)
                    view.requestLayout()
                }
                api.connectionDetails?.let { saveScale(it, percent) }
            }
            scaleToast?.cancel()
            scaleToast = Toast.makeText(this, getString(R.string.scale_percent, (client.scale * 100).roundToInt()),
                Toast.LENGTH_SHORT).also { it.show() }
        }
    }

    /**
     * The scale chosen with the volume keys is kept for the next connections.
     */
    private fun saveScale(server: ServerDetails, percent: Int) {
        server.scalePercent = percent
        val db = ConfigDatabase.getInstance()
        db.configs.getById(server.id)
            .subscribeOn(Schedulers.io())
            .subscribe({ saved ->
                saved.scalePercent = percent
                db.configs.save(saved)
            }, { Timber.w(it, "Cannot save the scale") })
    }

    /**
     * Adds the application of this window to the home screen: the server's menu says how to
     * start it, or else the command which started it.
     */
    private fun addToHomeScreen() {
        serviceBinderFragment.whenXpraAvailable { api ->
            val window = api.xpraClient.getWindow(windowId) ?: return@whenXpraAvailable
            val server = api.connectionDetails ?: return@whenXpraAvailable
            val app = api.xpraClient.serverApps.firstOrNull { it.matchesWindow(window.windowClasses) }
            val command = window.command
            val windowClass = window.windowClasses.lastOrNull { it.isNotEmpty() }
            when {
                app != null -> AppShortcuts.pin(this, server, app.name, app.command, app.wmClass,
                    AppShortcuts.decodeIcon(app) ?: window.icon)
                !command.isNullOrBlank() -> AppShortcuts.pin(this, server, windowClass ?: window.label(this, emptyList()),
                    command, windowClass, window.icon)
                else -> Toast.makeText(this, R.string.app_not_found, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Each window has a task of its own, like an app: show its title and icon in the recent apps.
     */
    @Suppress("DEPRECATION")
    private fun updateTaskDescription(window: AndroidXpraWindow) {
        setTaskDescription(ActivityManager.TaskDescription(window.label(this, serverApps()), window.icon))
    }

    private fun toggleKeyboard(view: View?) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        if (view != null) {
            val imeVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
            if (!imm.isActive(view) || !imeVisible) {
                view.isFocusable = true
                view.isFocusableInTouchMode = true
                if (view.requestFocus()) {
                    imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            } else {
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    override fun onWindowCreated(window: AndroidXpraWindow) {
        if (restoring) {
            // the windows the server had: see onReconnected
            return
        }
        if (window.hasParent(windowId)) {
            runOnUiThread {
                val proxyView = ProxyView(this, window)
                binding.workspaceView.addView(proxyView)
                window.addWindowListener(XpraWindowHandler(proxyView))
            }
        } else {
            startActivity(Intents.createXpraIntent(this, window.id))
        }
    }

    override fun onWindowLost(window: AndroidXpraWindow) {
        // each window view removes itself, when is lost
    }

    override fun onMetadataChanged(window: AndroidXpraWindow) {
        title = window.label(this, serverApps())
        updateTaskDescription(window)
    }

    override fun onIconChanged(window: AndroidXpraWindow) {
        supportActionBar?.setIcon(window.iconDrawable)
        updateTaskDescription(window)
    }

    override fun onLost(window: AndroidXpraWindow) {
        finish()
    }

    override fun onConnected(serverDetails: ServerDetails) {
        if (restoring) {
            serviceBinderFragment.whenXpraAvailable { api -> onReconnected(api) }
        }
    }

    override fun onDisconnected(serverDetails: ServerDetails) {
        finish()
    }

    override fun onConnectionError(serverDetails: ServerDetails, e: IOException) {
        finish()
    }


    inner class XpraWindowHandler(private val proxyView: ProxyView) : XpraWindowListener {
        override fun onMetadataChanged(window: AndroidXpraWindow?) {
        }

        override fun onIconChanged(window: AndroidXpraWindow?) {
        }

        override fun onLost(window: AndroidXpraWindow?) {
            binding.workspaceView.removeView(proxyView)
        }
    }


    private companion object {
        /** the scales the volume keys go through, in percent: see the resolution setting */
        val SCALE_LEVELS = intArrayOf(100, 125, 150, 175, 200, 225, 250, 275, 300, 350, 400)
        const val PREFS_NAME = "view_settings"
        const val PREF_TOUCHPAD_MODE = "touchpad_mode"
    }
}
