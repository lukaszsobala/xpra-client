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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import xpra.client.ClipboardSync

/**
 * The device's clipboard, as seen by [ClipboardSync].
 *
 * Android only lets the app in the foreground read the clipboard, so the device's clipboard is
 * sent to the server when a window screen gets the focus, see [sendToServer].
 */
class AndroidClipboard(context: Context) : ClipboardSync.LocalClipboard {

    private val clipboardManager = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun setText(text: String) {
        mainHandler.post {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(LABEL, text))
        }
    }

    companion object {
        private const val LABEL = "Xpra"

        /**
         * Sends the text of the device's clipboard to the server, if it changed.
         * Call it from an activity that has the focus.
         */
        fun sendToServer(context: Context, clipboard: ClipboardSync?) {
            clipboard ?: return
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip ?: return
            if (clip.itemCount == 0) {
                return
            }
            val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
            clipboard.onLocalText(text)
        }
    }
}
