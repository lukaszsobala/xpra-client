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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.github.jksiezni.xpra.client.AndroidXpraWindow
import timber.log.Timber
import xpra.client.ServerApp
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * The icons of the windows of the server's applications, used for the applications of which
 * the server sends no icon with its menu: servers often cannot read the old XPM icons which
 * many applications still have. They are kept for the next connections.
 */
object WindowIcons {

    private const val DIR = "app_icons"

    /** the icons read from the disk, or learned; null when there is none */
    private val icons = HashMap<String, Bitmap?>()

    private val changes = MutableLiveData(0)

    /**
     * Changes when an application gets the icon of its window.
     */
    val changed: LiveData<Int> = changes

    fun get(context: Context, serverId: Int, command: String): Bitmap? {
        val key = key(serverId, command)
        synchronized(icons) {
            if (icons.containsKey(key)) {
                return icons[key]
            }
        }
        val file = file(context, key)
        val bitmap = if (file.isFile) BitmapFactory.decodeFile(file.path) else null
        synchronized(icons) {
            icons[key] = bitmap
        }
        return bitmap
    }

    /**
     * Keeps the icon of a window for the applications without one which it belongs to, and
     * updates their home screen icons. Called on the main thread.
     */
    fun learn(context: Context, serverId: Int, apps: List<ServerApp>, window: AndroidXpraWindow) {
        val icon = window.icon ?: return
        if (serverId < 0 || window.hasParent() || window.isOverrideRedirect) {
            return
        }
        var learned = false
        for (app in apps) {
            if (app.iconData != null || !app.matchesWindow(window.windowClasses)) {
                continue
            }
            if (get(context, serverId, app.command)?.sameAs(icon) == true) {
                continue
            }
            val key = key(serverId, app.command)
            synchronized(icons) {
                icons[key] = icon
            }
            save(file(context, key), icon)
            AppShortcuts.updateIcon(context, serverId, app, icon)
            learned = true
        }
        if (learned) {
            changes.value = (changes.value ?: 0) + 1
        }
    }

    private fun save(file: File, icon: Bitmap) {
        Thread {
            try {
                file.parentFile?.mkdirs()
                FileOutputStream(file).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (e: Exception) {
                Timber.w(e, "Cannot save the icon %s", file)
            }
        }.start()
    }

    private fun file(context: Context, key: String) = File(File(context.filesDir, DIR), "$key.png")

    private fun key(serverId: Int, command: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$serverId|$command".toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
