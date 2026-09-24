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

    private class Key(val label: String, val keysym: String, val modifier: Boolean = false, val icon: Int = 0)

    private val keys = listOf(
        // the most used keys first, so that they fit on the screen without scrolling:
        Key("Esc", "Escape"),
        Key("Tab", "Tab"),
        Key("Ctrl", "Control_L", true),
        Key("Alt", "Alt_L", true),
        Key("Left", "Left", icon = R.drawable.ic_key_left_24),
        Key("Down", "Down", icon = R.drawable.ic_key_down_24),
        Key("Up", "Up", icon = R.drawable.ic_key_up_24),
        Key("Right", "Right", icon = R.drawable.ic_key_right_24),
        Key("Home", "Home"),
        Key("End", "End"),
        Key("PgUp", "Prior"),
        Key("PgDn", "Next"),
        Key("Del", "Delete"),
        Key("Super", "Super_L", true),
        Key("Shift", "Shift_L", true),
        Key("Ins", "Insert"),
    ) + (1..12).map { Key("F$it", "F$it") }

    private val modifierButtons = mutableMapOf<String, MaterialButton>()

    init {
        isHorizontalScrollBarEnabled = false
        // fade out the keys at the edges, to show that there are more to scroll to:
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength(dp(32))
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val spacing = dp(1)
        row.setPadding(0, dp(2), 0, dp(2))
        for (key in keys) {
            val button = MaterialButton(context, null,
                if (key.modifier) com.google.android.material.R.attr.materialButtonOutlinedStyle
                else androidx.appcompat.R.attr.borderlessButtonStyle).apply {
                if (key.icon != 0) {
                    // the arrows: icons, which cannot be mistaken for the backspace key
                    setIconResource(key.icon)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                    iconPadding = 0
                    iconSize = dp(20)
                } else {
                    text = key.label
                }
                isAllCaps = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                minWidth = dp(40)
                minimumWidth = dp(40)
                minHeight = 0
                minimumHeight = 0
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(8)
                setPadding(dp(6), 0, dp(6), 0)
                // the keyboard must stay attached to the window, not move to the buttons:
                isFocusable = false
                contentDescription = key.label
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
            row.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32)).apply {
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
