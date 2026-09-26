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
 * screen is off, which drops the connection: the app asks the user to exempt it.
 *
 * It opens the battery optimisation settings, rather than asking directly with
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS: Google Play only allows the permission that
 * needs to a few kinds of apps, which a remote desktop client is not one of.
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
     * Shows the settings where the app can be exempted, or changed back: the battery
     * optimisation list, or else the settings of the app, where some phones keep it.
     */
    fun request(activity: Activity) {
        val hint = if (isAllowed(activity)) {
            activity.getString(R.string.background_running_allowed)
        } else {
            activity.getString(R.string.background_running_how, activity.getString(R.string.app_name))
        }
        Toast.makeText(activity, hint, Toast.LENGTH_LONG).show()
        try {
            activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No battery optimisation settings")
            try {
                activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", activity.packageName, null)))
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(activity, R.string.background_running_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
}
