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
import android.graphics.SurfaceTexture
import android.view.*
import com.github.jksiezni.xpra.client.AndroidXpraWindow
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.max

/**
 * A proxy view for Xpra windows, dialogs, popups, menus, etc.
 *
 * Created in code only.
 */
@SuppressLint("ViewConstructor")
class ProxyView(context: Context, val window: AndroidXpraWindow) : TextureView(context) {

    init {
        surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                window.show(surface, width, height)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                Timber.v("onSurfaceTextureSizeChanged(): windowId=${window.id}, ${width}x${height}")
                window.resize(width, height)
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                window.hide(surface)
                return false
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // do nothing
            }
        }
        setOnTouchListener(TouchHandler())
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (window.hasParent()) {
            setMeasuredDimension((window.width*window.scale).toInt(), (window.height*window.scale).toInt())
        } else {
            setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
            val minWidth = (window.minimumWidth * window.scale).toInt()
            val minHeight = (window.minimumHeight * window.scale).toInt()

            // adjust to size constraints
            if (minWidth > measuredWidth || minHeight > measuredHeight) {
                setMeasuredDimension(max(minWidth, measuredWidth), max(minHeight, measuredHeight))
            }
        }
    }

    override fun getLayoutParams(): ViewGroup.LayoutParams? {
        val params = super.getLayoutParams()
        if (params is ViewGroup.MarginLayoutParams) {
            if (window.hasParent()) {
                params.leftMargin = window.scaledX
                params.topMargin = window.scaledY
            }
        }
        return params
    }

    /**
     * Turns touches into mouse events:
     * - a tap is a left click, and dragging a finger drags with the left button held down,
     * - a long press is a right click,
     * - dragging two fingers scrolls, like a mouse wheel.
     */
    inner class TouchHandler : OnTouchListener {

        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val scrollStep = SCROLL_STEP_DP * resources.displayMetrics.density

        /** where the finger went down, in view coordinates */
        private var downX = 0f
        private var downY = 0f
        private var state = State.IDLE
        private var scrollX = 0f
        private var scrollY = 0f

        private val longPress = Runnable {
            if (state == State.PENDING) {
                state = State.DONE
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                click(3, downX, downY)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            event.offsetLocation(x, y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    state = State.PENDING
                    window.movePointer(toWindowX(downX), toWindowY(downY))
                    postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (event.pointerCount == 2) {
                        removeCallbacks(longPress)
                        if (state == State.DRAGGING) {
                            button(1, false, event.x, event.y)
                        }
                        state = State.SCROLLING
                        scrollX = averageX(event)
                        scrollY = averageY(event)
                    }
                }
                MotionEvent.ACTION_MOVE -> when (state) {
                    State.PENDING -> if (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop) {
                        removeCallbacks(longPress)
                        state = State.DRAGGING
                        button(1, true, downX, downY)
                        window.movePointer(toWindowX(event.x), toWindowY(event.y))
                    }
                    State.DRAGGING -> window.movePointer(toWindowX(event.x), toWindowY(event.y))
                    State.SCROLLING -> if (event.pointerCount >= 2) scroll(averageX(event), averageY(event))
                    else -> {}
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    when (state) {
                        State.PENDING -> click(1, downX, downY)
                        State.DRAGGING -> button(1, false, event.x, event.y)
                        else -> {}
                    }
                    state = State.IDLE
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPress)
                    if (state == State.DRAGGING) {
                        button(1, false, event.x, event.y)
                    }
                    state = State.IDLE
                }
            }
            return true
        }

        /**
         * Sends a wheel click for each step the fingers moved: moving them up scrolls down,
         * as the content follows the fingers.
         */
        private fun scroll(x: Float, y: Float) {
            while (y - scrollY >= scrollStep) {
                click(4, x, y)
                scrollY += scrollStep
            }
            while (scrollY - y >= scrollStep) {
                click(5, x, y)
                scrollY -= scrollStep
            }
            while (x - scrollX >= scrollStep) {
                click(6, x, y)
                scrollX += scrollStep
            }
            while (scrollX - x >= scrollStep) {
                click(7, x, y)
                scrollX -= scrollStep
            }
        }

        private fun click(button: Int, x: Float, y: Float) {
            button(button, true, x, y)
            button(button, false, x, y)
        }

        private fun button(button: Int, pressed: Boolean, x: Float, y: Float) {
            val wx = toWindowX(x)
            val wy = toWindowY(y)
            window.movePointer(wx, wy)
            window.mouseAction(button, pressed, wx, wy)
        }

        private fun toWindowX(x: Float) = (max(x, 0f) / window.scale).toInt()

        private fun toWindowY(y: Float) = (max(y, 0f) / window.scale).toInt()

        private fun averageX(event: MotionEvent) = (event.getX(0) + event.getX(1)) / 2

        private fun averageY(event: MotionEvent) = (event.getY(0) + event.getY(1)) / 2
    }

    private enum class State {
        IDLE,
        /** a finger is down, and it is not yet known whether it will tap, long press or drag */
        PENDING,
        DRAGGING,
        SCROLLING,
        /** the gesture was handled already (ie: a long press), ignore the rest of it */
        DONE,
    }

    private companion object {
        /** how far two fingers move for each mouse wheel click */
        const val SCROLL_STEP_DP = 24f
    }
}
