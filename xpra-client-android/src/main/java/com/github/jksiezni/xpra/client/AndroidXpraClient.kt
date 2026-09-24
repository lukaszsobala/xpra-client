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
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.jksiezni.xpra.config.ServerDetails
import com.github.jksiezni.xpra.gl.GLComposer
import timber.log.Timber
import xpra.client.ServerApp
import xpra.client.XpraClient
import xpra.client.XpraWindow
import xpra.protocol.PictureEncoding
import xpra.protocol.packets.DrawPacket
import xpra.protocol.packets.NewWindow
import xpra.protocol.packets.NewWindowOverrideRedirect
import java.util.concurrent.CopyOnWriteArrayList

class AndroidXpraClient(private val context: Context) : XpraClient(0, 0, PICTURE_ENCODINGS, AndroidXpraKeyboard()) {

    private val windowsLiveData = MutableLiveData<Collection<XpraWindow>>()
    private val listeners: MutableList<XpraEventListener> = CopyOnWriteArrayList()
    private val serverAppsLiveData = MutableLiveData<List<ServerApp>>(emptyList())

    private val mainHandler = Handler(Looper.getMainLooper())
    private val startupActions = mutableListOf<Runnable>()

    private val composer = GLComposer(this::onDrawFinished) { windowId -> getWindow(windowId)?.requestRefresh() }

    /**
     * How many screen pixels are used for one pixel of the remote windows.
     */
    var scale: Float = context.resources.displayMetrics.density
        private set

    init {
        applySettings(ServerDetails())
    }

    /**
     * Applies the per-connection settings, before connecting.
     */
    fun applySettings(serverDetails: ServerDetails) {
        val dm = context.resources.displayMetrics
        scale = if (serverDetails.scalePercent > 0) serverDetails.scalePercent / 100f else dm.density
        updateDesktopSize(dm)
        setPictureEncoding(serverDetails.pictureEncoding)
        if (serverDetails.clipboardSharing) {
            enableClipboard(AndroidClipboard(context))
        } else {
            disableClipboard()
        }
    }

    /**
     * Makes the server's virtual screen match the area our windows can use, ie: after the
     * device was rotated. Servers clamp windows to their screen size.
     */
    fun updateDesktopSize(dm: DisplayMetrics) {
        setDesktopSize((dm.widthPixels / scale).toInt(), (dm.heightPixels / scale).toInt())
    }

    /**
     * The window on the screen, ie: of the activity in the foreground.
     */
    @Volatile
    var activeWindowId = 0

    override fun onCreateWindow(wnd: NewWindow, parentWindow: XpraWindow?): XpraWindow {
        val parent = if (parentWindow != null) {
            getWindow(parentWindow.id)
        } else if (wnd is NewWindowOverrideRedirect || wnd.isOverrideRedirect) {
            // tooltips, and some menus, do not say which window they belong to: show them over
            // the window on the screen, rather than on a screen of their own
            getWindow(activeWindowId)
        } else {
            null
        }
        return AndroidXpraWindow(wnd, context, composer, scale, parent)
    }

    override fun onWindowStarted(window: XpraWindow) {
        super.onWindowStarted(window)
        windowsLiveData.postValue(windows.filter { !it.hasParent() })
        listeners.forEach { it.onWindowCreated(window as AndroidXpraWindow) }
    }

    override fun onWindowMetadataUpdated(window: XpraWindow) {
        super.onWindowMetadataUpdated(window)
        windowsLiveData.postValue(windows.filter { !it.hasParent() })
    }

    override fun onDestroyWindow(window: XpraWindow) {
        super.onDestroyWindow(window)
        val androidXpraWindow = window as AndroidXpraWindow
        androidXpraWindow.release()
        listeners.forEach { it.onWindowLost(androidXpraWindow) }
        windowsLiveData.postValue(windows.filter { !it.hasParent() })
    }

    override fun getWindow(windowId: Int): AndroidXpraWindow? {
        return super.getWindow(windowId) as AndroidXpraWindow?
    }

    fun getWindowsLiveData(): LiveData<Collection<XpraWindow>> {
        return windowsLiveData
    }

    /**
     * The applications of the server, from its menu.
     */
    fun getServerAppsLiveData(): LiveData<List<ServerApp>> {
        return serverAppsLiveData
    }

    override fun onServerAppsChanged(apps: List<ServerApp>) {
        serverAppsLiveData.postValue(apps)
    }

    /**
     * Runs the action on the main thread, once the server has sent the windows it already had.
     */
    fun whenStartupComplete(action: Runnable) {
        synchronized(startupActions) {
            if (!isStartupComplete) {
                startupActions.add(action)
                return
            }
        }
        mainHandler.post(action)
    }

    override fun onStartupComplete() {
        super.onStartupComplete()
        val actions = synchronized(startupActions) {
            startupActions.toList().also { startupActions.clear() }
        }
        actions.forEach { mainHandler.post(it) }
    }

    override fun onDisconnect() {
        super.onDisconnect()
        synchronized(startupActions) {
            startupActions.clear()
        }
        serverAppsLiveData.postValue(emptyList())
    }

    fun addEventListener(listener: XpraEventListener) {
        listeners.add(listener)
    }

    fun removeEventListener(listener: XpraEventListener) {
        listeners.remove(listener)
    }

    private fun onDrawFinished(drawPacket: DrawPacket, decodeTime: Long) {
        Timber.v("onDrawFinished() decodeTime=%d", decodeTime)
        AndroidXpraWindow.sendDamageSequence(sender, drawPacket, decodeTime)
    }

    companion object {
        private val PICTURE_ENCODINGS = arrayOf(
                PictureEncoding.png,
                PictureEncoding.pngL,
                PictureEncoding.pngP,
                PictureEncoding.jpeg,
                PictureEncoding.rgb24,
                PictureEncoding.rgb32
        )
    }
}
