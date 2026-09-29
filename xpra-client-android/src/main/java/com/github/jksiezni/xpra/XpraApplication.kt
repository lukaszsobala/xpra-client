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

package com.github.jksiezni.xpra

import android.app.Application
import android.util.Log
import com.github.jksiezni.xpra.config.ConfigDatabase
import com.github.jksiezni.xpra.ssh.SshKeys
import com.google.android.material.color.DynamicColors
import timber.log.Timber
import java.util.logging.Level
import java.util.logging.Logger

class XpraApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Material You: take the theme colours from the wallpaper, on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
        ConfigDatabase.setup(this)
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree())
        logToTimber()
        ConfigDatabase.inBackground {
            SshKeys.cleanUp(this, ConfigDatabase.getInstance().configs)
        }
    }

    /**
     * The logs of xpra-common (java.util.logging) go through Timber, like those of the app.
     */
    private fun logToTimber() {
        val root = Logger.getLogger("")
        root.handlers.forEach { root.removeHandler(it) }
        root.addHandler(TimberLogHandler())
        // the messages which ReleaseTree drops are not even built
        root.level = if (BuildConfig.DEBUG) Level.ALL else Level.WARNING
    }

    /**
     * Release builds only log the problems, as the debug messages describe the traffic with
     * the server, ie: the contents of the clipboard.
     */
    private class ReleaseTree : Timber.DebugTree() {
        override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN
    }
}
