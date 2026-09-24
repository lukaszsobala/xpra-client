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
@file:JvmName("EdgeToEdgeInsets")

package com.github.jksiezni.xpra.view

import android.graphics.Color
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlin.math.max

/**
 * Draws the activity edge-to-edge (enforced from Android 15) and keeps its content
 * clear of the system bars: [appBar] is padded below the status bar, while [root]
 * is padded for the remaining bars, display cutouts and the soft keyboard.
 */
fun setupEdgeToEdge(activity: ComponentActivity, root: View, appBar: View) {
    // dark system bar icons on the light theme, light ones on the dark theme
    val barStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
    activity.enableEdgeToEdge(barStyle, barStyle)

    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        appBar.updatePadding(top = bars.top)
        view.updatePadding(left = bars.left, right = bars.right, bottom = max(bars.bottom, ime.bottom))
        WindowInsetsCompat.CONSUMED
    }
}
