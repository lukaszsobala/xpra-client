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

package com.github.jksiezni.xpra.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.caverock.androidsvg.SVG
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The icons of the server's applications, which it sends as PNG, JPEG or SVG images.
 */
object AppIcons {

    /**
     * The size of an adaptive icon, of which only the middle 72dp are sure to be visible.
     */
    private const val ADAPTIVE_ICON_DP = 108

    /**
     * How much of an adaptive icon the application's icon takes.
     */
    private const val ADAPTIVE_ICON_CONTENT = 0.5f

    /**
     * The backgrounds of the icons made from the first letter of an application's name.
     */
    private val LETTER_COLORS = intArrayOf(
        0xFF6750A4.toInt(), 0xFF386A20.toInt(), 0xFF006A6A.toInt(), 0xFF9C4146.toInt(),
        0xFF7D5260.toInt(), 0xFF00639B.toInt(), 0xFF8B5000.toInt(), 0xFF5C5F00.toInt()
    )

    /**
     * Decodes an icon sent by the server.
     *
     * @return the icon, or null if there is none or it cannot be read
     */
    fun decode(data: ByteArray?, type: String?, sizePx: Int): Bitmap? {
        if (data == null || data.isEmpty()) {
            return null
        }
        return try {
            if (type.equals("svg", ignoreCase = true) || isSvg(data)) {
                renderSvg(data, sizePx)
            } else {
                BitmapFactory.decodeByteArray(data, 0, data.size)
            }
        } catch (e: Exception) {
            Timber.w(e, "Cannot read an icon of type %s", type)
            null
        }
    }

    private fun isSvg(data: ByteArray): Boolean {
        val start = String(data, 0, minOf(data.size, 256), Charsets.UTF_8)
        return start.contains("<svg") || start.trimStart().startsWith("<?xml")
    }

    private fun renderSvg(data: ByteArray, sizePx: Int): Bitmap {
        val svg = SVG.getFromInputStream(ByteArrayInputStream(data))
        if (svg.documentViewBox == null && svg.documentWidth > 0 && svg.documentHeight > 0) {
            svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
        }
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(bitmap), RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()))
        return bitmap
    }

    /**
     * An icon for an application without one: the first letter of its name.
     */
    fun letterIcon(name: String, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = letterColor(name)
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
        drawLetter(canvas, name, sizePx / 2f, sizePx / 2f, sizePx * 0.5f)
        return bitmap
    }

    private fun letterColor(name: String) = LETTER_COLORS[abs(name.hashCode() % LETTER_COLORS.size)]

    private fun drawLetter(canvas: Canvas, name: String, centerX: Float, centerY: Float, textSize: Float) {
        val letter = name.trim().take(1).uppercase(Locale.getDefault()).ifEmpty { "?" }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textSize = textSize
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        val bounds = Rect()
        paint.getTextBounds(letter, 0, letter.length, bounds)
        canvas.drawText(letter, centerX, centerY - bounds.exactCenterY(), paint)
    }

    /**
     * The picture of an adaptive icon for the home screen: the application's icon in the middle
     * of a white background, or its first letter on a coloured one.
     */
    fun adaptiveIcon(context: Context, name: String, icon: Bitmap?): Bitmap {
        val sizePx = (ADAPTIVE_ICON_DP * context.resources.displayMetrics.density).roundToInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val center = sizePx / 2f
        if (icon == null) {
            canvas.drawColor(letterColor(name))
            drawLetter(canvas, name, center, center, sizePx * 0.3f)
            return bitmap
        }
        canvas.drawColor(Color.WHITE)
        val contentSize = sizePx * ADAPTIVE_ICON_CONTENT
        // keep the proportions of icons which are not square:
        val scale = contentSize / maxOf(icon.width, icon.height)
        val width = icon.width * scale
        val height = icon.height * scale
        val dst = RectF(center - width / 2, center - height / 2, center + width / 2, center + height / 2)
        canvas.drawBitmap(icon, null, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return bitmap
    }
}
