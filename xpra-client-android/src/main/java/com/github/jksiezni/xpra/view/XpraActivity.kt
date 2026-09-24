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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.jksiezni.xpra.R
import com.github.jksiezni.xpra.client.*
import com.github.jksiezni.xpra.client.AndroidXpraWindow.XpraWindowListener
import com.github.jksiezni.xpra.view.Intents.getWindowId
import com.github.jksiezni.xpra.view.Intents.isValidXpraActivityIntent
import com.github.jksiezni.xpra.config.ServerDetails
import com.github.jksiezni.xpra.databinding.ActivityXpraBinding
import timber.log.Timber
import xpra.client.KeyboardInput
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
        setupEdgeToEdge(this, binding.root, binding.toolbar)
        if (!isValidXpraActivityIntent(intent)) {
            finish()
            return
        }
        windowId = getWindowId(intent)
        setSupportActionBar(binding.toolbar)
        binding.workspaceView.touchpadMode = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_TOUCHPAD_MODE, false)

        serviceBinderFragment.whenXpraAvailable { api ->
            val rootWindow = api.xpraClient.getWindow(windowId)
            if (rootWindow == null) {
                Timber.w("Window with windowId=%d not found", windowId)
                finish()
                return@whenXpraAvailable
            }
            title = rootWindow.title
            api.registerConnectionListener(this)
            api.xpraClient.addEventListener(this)
            rootWindow.addWindowListener(this)
            // the activity is created again when the device rotates:
            api.xpraClient.updateDesktopSize(resources.displayMetrics)
            restoreProxyViewHierarchy(rootWindow)
            binding.workspaceView.keyboardInput = KeyboardInput(rootWindow)
            binding.workspaceView.requestFocus()
            setResult(RESULT_OK)
        }
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
            val window = api.xpraClient.getWindow(windowId)
            window?.removeWindowListener(this)
            api.xpraClient.removeEventListener(this)
            api.unregisterConnectionListener(this)
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
        title = window.title
    }

    override fun onIconChanged(window: AndroidXpraWindow) {
        supportActionBar?.setIcon(window.iconDrawable)
    }

    override fun onLost(window: AndroidXpraWindow) {
        finish()
    }

    override fun onConnected(serverDetails: ServerDetails) {
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
        const val PREFS_NAME = "view_settings"
        const val PREF_TOUCHPAD_MODE = "touchpad_mode"
    }
}
