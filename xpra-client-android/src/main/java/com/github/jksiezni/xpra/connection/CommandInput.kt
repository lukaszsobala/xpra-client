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


package com.github.jksiezni.xpra.connection

import android.graphics.Typeface
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/**
 * A field where the user types a command line, not a sentence.
 */
object CommandInput {

    /**
     * Asks for a keyboard without capital letters, suggestions or auto-correction, in a
     * monospace font: [onGo] runs when the keyboard's Go key is pressed.
     */
    @JvmStatic
    fun configure(editText: EditText, onGo: Runnable) {
        editText.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        editText.typeface = Typeface.MONOSPACE
        editText.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                onGo.run()
                true
            } else {
                false
            }
        }
    }
}
