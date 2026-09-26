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

package com.github.jksiezni.xpra.help

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.widget.TextViewCompat
import com.github.jksiezni.xpra.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * How to use the app: what it needs, and its controls, next to the icons of their buttons.
 */
object HowToUse {

    private sealed class Item
    private class Heading(@StringRes val text: Int) : Item()
    private class Row(@DrawableRes val icon: Int, @StringRes val text: Int) : Item()

    private val items = listOf(
        Heading(R.string.help_servers),
        Row(R.drawable.ic_baseline_dns_24, R.string.help_add_server),
        Row(R.drawable.ic_baseline_add_to_home_screen_24, R.string.help_pin_app),
        Row(R.drawable.ic_baseline_add_24, R.string.help_start_command),
        Heading(R.string.help_touch),
        Row(R.drawable.ic_baseline_touch_app_24, R.string.help_direct_touch),
        Row(R.drawable.ic_baseline_mouse_24, R.string.help_touchpad),
        Heading(R.string.help_zoom),
        Row(R.drawable.ic_baseline_zoom_in_24, R.string.help_pinch),
        Row(R.drawable.ic_baseline_volume_up_24, R.string.help_volume_keys),
        Heading(R.string.help_keyboard_title),
        Row(R.drawable.ic_baseline_keyboard_24, R.string.help_keyboard),
        Row(R.drawable.ic_baseline_close_24, R.string.help_close),
        Row(0, R.string.help_background),
    )

    fun show(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.how_to_use)
            .setView(content(context))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun content(context: Context): ScrollView {
        val dp = context.resources.displayMetrics.density
        fun px(value: Int) = (value * dp).toInt()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(8), px(24), px(8))
        }
        column.addView(TextView(context).apply {
            TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            text = context.getText(R.string.help_intro)
            movementMethod = LinkMovementMethod.getInstance()
        })
        for (item in items) {
            when (item) {
                is Heading -> column.addView(TextView(context).apply {
                    TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
                    setText(item.text)
                    setPadding(0, px(16), 0, px(4))
                })
                is Row -> column.addView(LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, px(4), 0, px(4))
                    addView(ImageView(context).apply {
                        if (item.icon != 0) setImageResource(item.icon)
                    }, LinearLayout.LayoutParams(px(24), px(24)).apply { marginEnd = px(16) })
                    addView(TextView(context).apply {
                        TextViewCompat.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                        setText(item.text)
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                })
            }
        }
        return ScrollView(context).apply { addView(column) }
    }
}
