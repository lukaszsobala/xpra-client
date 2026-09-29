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

import android.util.Log
import timber.log.Timber
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SimpleFormatter

/**
 * Passes the java.util.logging records, ie: of xpra-common, to Timber, tagged with the
 * simple name of their class.
 */
class TimberLogHandler : Handler() {

    private val messages = SimpleFormatter()

    override fun publish(record: LogRecord) {
        val level = record.level.intValue()
        val priority = when {
            level >= Level.SEVERE.intValue() -> Log.ERROR
            level >= Level.WARNING.intValue() -> Log.WARN
            level >= Level.INFO.intValue() -> Log.INFO
            level >= Level.FINE.intValue() -> Log.DEBUG
            else -> Log.VERBOSE
        }
        val tag = record.loggerName?.substringAfterLast('.')
        // no arguments: the message is not formatted again by Timber
        Timber.tag(tag ?: "xpra").log(priority, record.thrown, messages.formatMessage(record))
    }

    override fun flush() {
    }

    override fun close() {
    }
}
