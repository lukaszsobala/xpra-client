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
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.github.jksiezni.xpra.R
import com.github.jksiezni.xpra.config.ServerDetails
import xpra.client.ServerApp
import java.security.MessageDigest

/**
 * Home screen icons, which start an application of a server as if it was an Android app.
 */
object AppShortcuts {

    const val ACTION_LAUNCH_APP = "com.github.jksiezni.xpra.action.LAUNCH_APP"
    const val EXTRA_SERVER_ID = "server_id"
    const val EXTRA_NAME = "name"
    const val EXTRA_COMMAND = "command"
    const val EXTRA_WM_CLASS = "wm_class"

    /**
     * The size of the icons decoded for the home screen.
     */
    private const val ICON_SIZE_PX = 192

    /**
     * Starts the application, or shows its window if it runs already, see [LaunchAppActivity].
     */
    fun launchIntent(context: Context, serverId: Int, name: String, command: String, wmClass: String?): Intent {
        return Intent(context, LaunchAppActivity::class.java).apply {
            action = ACTION_LAUNCH_APP
            putExtra(EXTRA_SERVER_ID, serverId)
            putExtra(EXTRA_NAME, name)
            putExtra(EXTRA_COMMAND, command)
            if (!wmClass.isNullOrEmpty()) {
                putExtra(EXTRA_WM_CLASS, wmClass)
            }
        }
    }

    fun launchIntent(context: Context, serverId: Int, app: ServerApp): Intent {
        return launchIntent(context, serverId, app.name, app.command, app.wmClass)
    }

    fun decodeIcon(app: ServerApp): Bitmap? = AppIcons.decode(app.iconData, app.iconType, ICON_SIZE_PX)

    /**
     * Asks the launcher to add the application to the home screen.
     *
     * @param icon - the application's icon, or null for its first letter
     */
    fun pin(context: Context, server: ServerDetails, name: String, command: String, wmClass: String?, icon: Bitmap?) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.pin_not_supported, Toast.LENGTH_LONG).show()
            return
        }
        val serverName = server.name?.takeIf { it.isNotBlank() } ?: server.host.orEmpty()
        val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(server.id, command))
            .setShortLabel(name)
            .setLongLabel(context.getString(R.string.app_on_server, name, serverName))
            .setIcon(IconCompat.createWithAdaptiveBitmap(AppIcons.adaptiveIcon(context, name, icon)))
            .setIntent(launchIntent(context, server.id, name, command, wmClass))
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    fun pin(context: Context, server: ServerDetails, app: ServerApp) {
        pin(context, server, app.name, app.command, app.wmClass, decodeIcon(app))
    }

    /**
     * The same application of a server always has the same shortcut, which is updated when
     * pinned again.
     */
    private fun shortcutId(serverId: Int, command: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(command.toByteArray(Charsets.UTF_8))
        return "app-$serverId-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
