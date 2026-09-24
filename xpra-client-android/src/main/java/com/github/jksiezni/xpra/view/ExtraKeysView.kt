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
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.github.jksiezni.xpra.R
import com.google.android.material.button.MaterialButton
import xpra.client.KeyboardInput
import xpra.client.KeyboardInput.Sticky

/**
 * A row of the keys that on-screen keyboards lack, shown above them: Esc, Tab, the modifiers,
 * the arrows, the navigation keys and the function keys.
 *
 * The modifiers are sticky: a tap holds one down for the next key, typed on this row or on the
 * on-screen keyboard, ie: Ctrl then C, and a long press holds it down until tapped again.
 */
class ExtraKeysView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    HorizontalScrollView(context, attrs) {

    /**
     * Where the keys go, ie: the window shown.
     */
    var keyboardInput: KeyboardInput? = null
        set(value) {
            field?.setStickyListener(null)
            field = value
            value?.setStickyListener { post { updateModifiers() } }
            updateModifiers()
        }

    private class Key(val label: String, val keysym: String, val modifier: Boolean = false)

    private val keys = listOf(
        Key("Esc", "Escape"),
        Key("Tab", "Tab"),
        Key("Ctrl", "Control_L", true),
        Key("Alt", "Alt_L", true),
        Key("Super", "Super_L", true),
        Key("Shift", "Shift_L", true),
        Key("←", "Left"),
        Key("↓", "Down"),
        Key("↑", "Up"),
        Key("→", "Right"),
        Key("Home", "Home"),
        Key("End", "End"),
        Key("PgUp", "Prior"),
        Key("PgDn", "Next"),
        Key("Ins", "Insert"),
        Key("Del", "Delete"),
    ) + (1..12).map { Key("F$it", "F$it") }

    private val modifierButtons = mutableMapOf<String, MaterialButton>()

    init {
        isHorizontalScrollBarEnabled = false
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val spacing = dp(2)
        for (key in keys) {
            val button = MaterialButton(context, null,
                if (key.modifier) com.google.android.material.R.attr.materialButtonOutlinedStyle
                else androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                text = key.label
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                minWidth = dp(48)
                minimumWidth = dp(48)
                minHeight = 0
                minimumHeight = 0
                insetTop = 0
                insetBottom = 0
                setPadding(dp(8), 0, dp(8), 0)
                // the keyboard must stay attached to the window, not move to the buttons:
                isFocusable = false
                contentDescription = key.keysym
            }
            if (key.modifier) {
                button.isCheckable = true
                button.setOnClickListener { onModifierClicked(key.keysym) }
                button.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    keyboardInput?.setSticky(key.keysym, Sticky.LOCKED)
                    updateModifiers()
                    true
                }
                modifierButtons[key.keysym] = button
            } else {
                button.setOnClickListener {
                    keyboardInput?.let { keyboard ->
                        keyboard.key(key.keysym, true)
                        keyboard.key(key.keysym, false)
                    }
                    updateModifiers()
                }
            }
            row.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                marginStart = spacing
                marginEnd = spacing
            })
        }
        addView(row)
    }

    private fun onModifierClicked(keysym: String) {
        val keyboard = keyboardInput ?: return
        keyboard.setSticky(keysym, if (keyboard.getSticky(keysym) == Sticky.OFF) Sticky.LATCHED else Sticky.OFF)
        updateModifiers()
    }

    private fun updateModifiers() {
        for ((keysym, button) in modifierButtons) {
            val state = keyboardInput?.getSticky(keysym) ?: Sticky.OFF
            val label = keys.first { it.keysym == keysym }.label
            button.isChecked = state != Sticky.OFF
            button.text = if (state == Sticky.LOCKED) context.getString(R.string.locked_key, label) else label
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
