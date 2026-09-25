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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import com.github.jksiezni.xpra.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber

/**
 * Battery optimisations let Android cut the network of apps in the background, ie: while the
 * screen is off, which drops the connection: the app asks to be exempted.
 */
object BackgroundRunning {

    private const val PREFS_NAME = "background_running"
    private const val PREF_DONT_ASK = "dont_ask"

    fun isAllowed(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Explains why, then asks, unless the app is exempted already or the user said not to ask.
     */
    fun askOnce(activity: Activity) {
        if (isAllowed(activity) || activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_DONT_ASK, false)) {
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.background_running_title)
            .setMessage(R.string.background_running_message)
            .setPositiveButton(R.string.allow) { _, _ -> request(activity) }
            .setNegativeButton(R.string.not_now, null)
            .setNeutralButton(R.string.dont_ask_again) { _, _ ->
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putBoolean(PREF_DONT_ASK, true)
                    .apply()
            }
            .show()
    }

    /**
     * Asks Android to exempt the app, or shows the settings where it can be changed back.
     */
    @SuppressLint("BatteryLife")
    fun request(activity: Activity) {
        val intent = if (isAllowed(activity)) {
            Toast.makeText(activity, R.string.background_running_allowed, Toast.LENGTH_LONG).show()
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + activity.packageName))
        }
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No battery optimisation settings")
            try {
                activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.background_running_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
}
