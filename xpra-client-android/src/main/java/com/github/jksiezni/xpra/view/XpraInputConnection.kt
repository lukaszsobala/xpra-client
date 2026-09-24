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

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.text.InputType
import com.github.jksiezni.xpra.client.AndroidXpraKeyboard
import xpra.client.KeyboardInput

/**
 * Receives the input of on-screen keyboards, which commit text rather than sending key events,
 * and types it into the remote window.
 *
 * The remote application owns the text, so there is nothing to edit locally: text being composed
 * is typed as it changes, and replaced with backspaces when the keyboard changes it.
 */
internal class XpraInputConnection(
    view: View,
    private val input: () -> KeyboardInput?
) : BaseInputConnection(view, false) {

    private var composing = ""

    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        replaceComposing(text.toString())
        composing = ""
        return true
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        replaceComposing(text.toString())
        return true
    }

    override fun finishComposingText(): Boolean {
        composing = ""
        return true
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        val keyboard = input() ?: return true
        repeat(beforeLength) { tap(keyboard, "BackSpace") }
        repeat(afterLength) { tap(keyboard, "Delete") }
        return true
    }

    override fun performEditorAction(editorAction: Int): Boolean {
        input()?.let { tap(it, "Return") }
        return true
    }

    /**
     * Types [text] in place of the text being composed, only sending what changed.
     */
    private fun replaceComposing(text: String) {
        val keyboard = input() ?: return
        val common = composing.commonPrefixWith(text).length
        repeat(composing.length - common) { tap(keyboard, "BackSpace") }
        keyboard.typeText(text.substring(common))
        composing = text
    }

    private fun tap(keyboard: KeyboardInput, keysym: String) {
        keyboard.key(keysym, true)
        keyboard.key(keysym, false)
    }

    companion object {
        /**
         * Asks for a keyboard without suggestions, auto-correction or text prediction,
         * like for a terminal: the remote application does its own editing.
         */
        fun configure(outAttrs: EditorInfo) {
            outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN or
                EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or
                EditorInfo.IME_ACTION_NONE
        }

        /**
         * Sends a key event, from a hardware keyboard or an on-screen keyboard's special keys.
         *
         * @return false if the key is not handled, ie: the back or volume keys
         */
        fun handleKeyEvent(keyboard: KeyboardInput, event: KeyEvent): Boolean {
            val pressed = when (event.action) {
                KeyEvent.ACTION_DOWN -> true
                KeyEvent.ACTION_UP -> false
                KeyEvent.ACTION_MULTIPLE -> {
                    event.characters?.let { keyboard.typeText(it) }
                    return event.characters != null
                }
                else -> return false
            }
            val special = AndroidXpraKeyboard.getSpecialKeysym(event.keyCode)
            if (special != null) {
                keyboard.key(special, pressed)
                return true
            }
            val unicode = event.unicodeChar
            if (unicode == 0 || event.isSystem) {
                return false
            }
            if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
                // a shortcut: the modifier keys are down already, send the key itself
                val base = event.getUnicodeChar(0)
                if (base == 0) {
                    return false
                }
                keyboard.key(KeyboardInput.getKeysym(base), pressed)
            } else if (pressed) {
                // a character: typed on key down, including auto-repeats
                keyboard.typeCharacter(unicode)
            }
            return true
        }
    }
}
