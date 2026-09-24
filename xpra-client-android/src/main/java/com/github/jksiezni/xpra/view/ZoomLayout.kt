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

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Zooms into its child with a pinch, ie: to read small text, without changing the remote
 * windows: moving the fingers while pinching also pans around. Touches still land where they
 * should, as Android maps them through the zoom of the child.
 *
 * Pinches are told apart from two-finger scrolling, which is passed on to the windows, by
 * the distance between the fingers changing.
 */
class ZoomLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    private val pinchSlop = ViewConfiguration.get(context).scaledTouchSlop * 2

    var zoom = 1f
        private set

    private var startSpan = 0f
    private var startZoom = 1f
    /** the point of the child under the fingers when the pinch started, in its unzoomed coordinates */
    private var anchorX = 0f
    private var anchorY = 0f
    private var pinching = false

    init {
        clipChildren = true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (ev.pointerCount == 2) {
                startSpan = span(ev)
            }
            MotionEvent.ACTION_MOVE -> if (ev.pointerCount == 2 && !pinching && startSpan > 0 &&
                abs(span(ev) - startSpan) > pinchSlop) {
                startPinch(ev)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> startSpan = 0f
        }
        return pinching
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                startPinch(event)
            }
            MotionEvent.ACTION_MOVE -> if (pinching && event.pointerCount >= 2) {
                val newZoom = (startZoom * span(event) / startSpan).coerceIn(MIN_ZOOM, MAX_ZOOM)
                // keep the anchor under the fingers:
                setZoom(newZoom, focusX(event) - anchorX * newZoom, focusY(event) - anchorY * newZoom)
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.pointerCount <= 2) {
                pinching = false
                if (zoom < SNAP_ZOOM) {
                    setZoom(1f, 0f, 0f)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                pinching = false
                startSpan = 0f
            }
        }
        return true
    }

    private fun startPinch(event: MotionEvent) {
        val child = getChildAt(0) ?: return
        pinching = true
        startSpan = span(event).coerceAtLeast(1f)
        startZoom = zoom
        anchorX = (focusX(event) - child.translationX) / zoom
        anchorY = (focusY(event) - child.translationY) / zoom
    }

    /**
     * Zooms the child, with its top left corner at the given position, kept so that the child
     * always covers this layout.
     */
    fun setZoom(newZoom: Float, left: Float, top: Float) {
        val child = getChildAt(0) ?: return
        zoom = newZoom
        child.pivotX = 0f
        child.pivotY = 0f
        child.scaleX = newZoom
        child.scaleY = newZoom
        child.translationX = left.coerceIn(width - child.width * newZoom, 0f)
        child.translationY = top.coerceIn(height - child.height * newZoom, 0f)
    }

    fun resetZoom() = setZoom(1f, 0f, 0f)

    /**
     * Pans, while zoomed in, so that a point of the child stays on the screen, ie: the pointer
     * which moves towards an edge.
     *
     * @param x - the point, in the (unzoomed) coordinates of the child
     */
    fun keepVisible(x: Float, y: Float) {
        val child = getChildAt(0) ?: return
        if (zoom <= 1f) {
            return
        }
        val margin = (EDGE_MARGIN_DP * resources.displayMetrics.density).coerceAtMost(minOf(width, height) / 4f)
        val onScreenX = child.translationX + x * zoom
        val onScreenY = child.translationY + y * zoom
        var left = child.translationX
        var top = child.translationY
        if (onScreenX < margin) {
            left += margin - onScreenX
        } else if (onScreenX > width - margin) {
            left -= onScreenX - (width - margin)
        }
        if (onScreenY < margin) {
            top += margin - onScreenY
        } else if (onScreenY > height - margin) {
            top -= onScreenY - (height - margin)
        }
        if (left != child.translationX || top != child.translationY) {
            setZoom(zoom, left, top)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        getChildAt(0)?.let { post { setZoom(zoom, it.translationX, it.translationY) } }
    }

    private fun span(event: MotionEvent) = hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

    private fun focusX(event: MotionEvent) = (event.getX(0) + event.getX(1)) / 2

    private fun focusY(event: MotionEvent) = (event.getY(0) + event.getY(1)) / 2

    private companion object {
        const val MIN_ZOOM = 1f
        const val MAX_ZOOM = 5f
        /** zooms this close to 1 go back to 1 */
        const val SNAP_ZOOM = 1.1f
        /** how close to an edge the pointer gets before panning */
        const val EDGE_MARGIN_DP = 48f
    }
}
