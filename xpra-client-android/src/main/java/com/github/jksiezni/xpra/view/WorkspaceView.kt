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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.Scroller
import androidx.core.math.MathUtils
import androidx.core.view.children
import xpra.client.KeyboardInput
import kotlin.math.abs

/**
 *
 */
class WorkspaceView : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    private val overflingDistance = ViewConfiguration.get(context).scaledOverflingDistance

    /**
     * Where the keyboard input goes, ie: the window shown by this workspace.
     */
    var keyboardInput: KeyboardInput? = null

    init {
        // receives the keys of hardware keyboards, and of on-screen keyboards once shown
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        XpraInputConnection.configure(outAttrs)
        return XpraInputConnection(this) { keyboardInput }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(event) || super.onKeyDown(keyCode, event)

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        handleKey(event) || super.onKeyUp(keyCode, event)

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean =
        handleKey(event) || super.onKeyMultiple(keyCode, repeatCount, event)

    private fun handleKey(event: KeyEvent): Boolean {
        val keyboard = keyboardInput ?: return false
        return XpraInputConnection.handleKeyEvent(keyboard, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (!gainFocus) {
            // do not leave modifiers stuck on the server
            keyboardInput?.releaseModifiers()
        }
    }

    override fun shouldDelayChildPressedState(): Boolean {
        return true
    }

    /**
     * In touchpad mode, the screen works like a laptop's touchpad moving a pointer drawn over
     * the windows, instead of the windows being touched directly.
     */
    var touchpadMode = false
        set(value) {
            field = value
            if (value) {
                // once laid out, when turned on as the view is created
                post {
                    touchpad.centerPointer()
                    invalidate()
                }
            }
            invalidate()
        }

    private val touchpad = Touchpad()

    // a real mouse is not a touchpad: let the windows have its events
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        touchpadMode && !ev.isFromSource(InputDevice.SOURCE_MOUSE)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (touchpadMode) {
            return touchpad.onTouch(event)
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    /**
     * Keeps a point of this view on the screen while zoomed in, see [ZoomLayout.keepVisible].
     */
    fun keepVisible(x: Float, y: Float) {
        (parent as? ZoomLayout)?.keepVisible(x, y)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (touchpadMode) {
            touchpad.drawPointer(canvas)
        }
    }

    /**
     * Turns touches into the moves and clicks of a pointer, like a touchpad:
     * - moving a finger moves the pointer,
     * - a tap is a left click, and tapping twice quickly is a double click,
     * - a tap followed by touching again and moving drags with the left button held down,
     * - a long press, or a tap with two fingers, is a right click,
     * - dragging two fingers scrolls, like a mouse wheel.
     */
    private inner class Touchpad {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val density = resources.displayMetrics.density
        private val scrollStep = SCROLL_STEP_DP * density

        /** the pointer, in the coordinates of the windows' views */
        private var pointerX = 0f
        private var pointerY = 0f
        private var lastX = 0f
        private var lastY = 0f
        private var downX = 0f
        private var downY = 0f
        private var downTime = 0L
        private var lastTapTime = 0L
        private var state = State.IDLE
        /** where the fingers were when the last mouse wheel click was sent */
        private var wheelX = 0f
        private var wheelY = 0f
        private var scrolled = false

        private val pointerPath = Path().apply {
            // an arrow, pointing up and left from (0, 0)
            moveTo(0f, 0f)
            lineTo(0f, 17f)
            lineTo(4.5f, 13f)
            lineTo(7.5f, 19.5f)
            lineTo(10f, 18.5f)
            lineTo(7f, 12f)
            lineTo(12.5f, 12f)
            close()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xffffffff.toInt()
            style = Paint.Style.FILL
        }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xff000000.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            strokeJoin = Paint.Join.ROUND
        }

        private val longPress = Runnable {
            if (state == State.PENDING) {
                state = State.DONE
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                click(3)
            }
        }

        fun centerPointer() {
            pointerX = scrollX + width / 2f
            pointerY = scrollY + height / 2f
        }

        fun drawPointer(canvas: Canvas) {
            canvas.save()
            canvas.translate(pointerX, pointerY)
            // the same size, whatever the zoom:
            canvas.scale(density / scaleX, density / scaleY)
            canvas.drawPath(pointerPath, fill)
            canvas.drawPath(pointerPath, outline)
            canvas.restore()
        }

        fun onTouch(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // on the screen, not in this view, which moves when it pans while zoomed in:
                    // the pointer would run away, pushing the view which pushes the pointer...
                    downX = event.rawX
                    downY = event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    downTime = event.eventTime
                    if (event.eventTime - lastTapTime < ViewConfiguration.getDoubleTapTimeout()) {
                        // touching again right after a tap: a double click, or a drag
                        state = State.DRAGGING
                        button(1, true)
                    } else {
                        state = State.PENDING
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                    }
                }
                MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount == 2) {
                    removeCallbacks(longPress)
                    if (state == State.DRAGGING) {
                        button(1, false)
                    }
                    state = State.SCROLLING
                    scrolled = false
                    wheelX = (event.getX(0) + event.getX(1)) / 2
                    wheelY = (event.getY(0) + event.getY(1)) / 2
                }
                MotionEvent.ACTION_MOVE -> {
                    when (state) {
                        State.PENDING -> if (abs(event.rawX - downX) > touchSlop || abs(event.rawY - downY) > touchSlop) {
                            removeCallbacks(longPress)
                            state = State.MOVING
                            movePointerBy((event.rawX - lastX) / scaleX, (event.rawY - lastY) / scaleY)
                        }
                        State.MOVING, State.DRAGGING -> movePointerBy((event.rawX - lastX) / scaleX, (event.rawY - lastY) / scaleY)
                        State.SCROLLING -> if (event.pointerCount >= 2) {
                            scroll((event.getX(0) + event.getX(1)) / 2, (event.getY(0) + event.getY(1)) / 2)
                        }
                        else -> {}
                    }
                    lastX = event.rawX
                    lastY = event.rawY
                }
                MotionEvent.ACTION_POINTER_UP -> if (state == State.SCROLLING) {
                    if (!scrolled && event.eventTime - downTime < ViewConfiguration.getLongPressTimeout()) {
                        // a tap with two fingers
                        click(3)
                    }
                    state = State.DONE
                }
                MotionEvent.ACTION_UP -> {
                    removeCallbacks(longPress)
                    when (state) {
                        State.PENDING -> {
                            click(1)
                            lastTapTime = event.eventTime
                        }
                        State.DRAGGING -> {
                            button(1, false)
                            lastTapTime = 0
                        }
                        else -> {}
                    }
                    state = State.IDLE
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(longPress)
                    if (state == State.DRAGGING) {
                        button(1, false)
                    }
                    state = State.IDLE
                }
            }
            return true
        }

        private fun movePointerBy(dx: Float, dy: Float) {
            val bounds = getScrollRange()
            pointerX = (pointerX + dx * POINTER_SPEED).coerceIn(bounds.left.toFloat(), (bounds.right - 1).toFloat().coerceAtLeast(0f))
            pointerY = (pointerY + dy * POINTER_SPEED).coerceIn(bounds.top.toFloat(), (bounds.bottom - 1).toFloat().coerceAtLeast(0f))
            target()?.let { view ->
                view.window.movePointer(toWindow(pointerX, view), toWindow(pointerY, view))
            }
            keepVisible(pointerX - scrollX, pointerY - scrollY)
            invalidate()
        }

        private fun scroll(x: Float, y: Float) {
            while (y - wheelY >= scrollStep) {
                click(4)
                wheelY += scrollStep
                scrolled = true
            }
            while (wheelY - y >= scrollStep) {
                click(5)
                wheelY -= scrollStep
                scrolled = true
            }
            while (x - wheelX >= scrollStep) {
                click(6)
                wheelX += scrollStep
                scrolled = true
            }
            while (wheelX - x >= scrollStep) {
                click(7)
                wheelX -= scrollStep
                scrolled = true
            }
        }

        private fun click(button: Int) {
            button(button, true)
            button(button, false)
        }

        private fun button(button: Int, pressed: Boolean) {
            val view = target() ?: return
            val x = toWindow(pointerX, view)
            val y = toWindow(pointerY, view)
            view.window.movePointer(x, y)
            view.window.mouseAction(button, pressed, x, y)
        }

        /**
         * @return the view of the window under the pointer: the topmost one, ie: a menu
         */
        private fun target(): ProxyView? {
            for (i in childCount - 1 downTo 0) {
                val child = getChildAt(i) as? ProxyView ?: continue
                if (pointerX >= child.left && pointerX < child.right && pointerY >= child.top && pointerY < child.bottom) {
                    return child
                }
            }
            return children.filterIsInstance<ProxyView>().firstOrNull()
        }

        /**
         * Windows use the coordinates of the server's screen, of which the views are a scaled copy.
         */
        private fun toWindow(v: Float, view: ProxyView) = (v.coerceAtLeast(0f) / view.window.scale).toInt()
    }

    private enum class State {
        IDLE,
        /** a finger is down, and it is not yet known whether it will tap, long press or move */
        PENDING,
        MOVING,
        DRAGGING,
        SCROLLING,
        /** the gesture was handled already (ie: a long press), ignore the rest of it */
        DONE,
    }

    override fun scrollBy(x: Int, y: Int) {
        val r = getScrollRange()
        val tx = MathUtils.clamp(scrollX + x, r.left, r.right - width)
        val ty = MathUtils.clamp(scrollY + y, r.top, r.bottom - height)
        super.scrollTo(tx, ty)
    }

    private fun getScrollRange(): Rect {
        val temp = Rect()
        return children.fold(Rect()) { acc, view ->
            view.getDrawingRect(temp)
            acc.apply { union(temp) }
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val oldX: Int = scrollX
            val oldY: Int = scrollY
            val x: Int = scroller.currX
            val y: Int = scroller.currY

            if (oldX != x || oldY != y) {
                val range = getScrollRange()
                overScrollBy(x - oldX, y - oldY, oldX, oldY, range.width(), range.height(),
                        overflingDistance, overflingDistance, false)
                onScrollChanged(scrollX, scrollY, oldX, oldY)
            }

            if (!awakenScrollBars()) {
                // Keep on drawing until the animation has finished.
                postInvalidateOnAnimation()
            }
        }
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        scrollTo(scrollX, scrollY)
        awakenScrollBars()
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        val range = getScrollRange()
        val viewport = Rect().apply { getDrawingRect(this) }

        var dx = 0
        var dy = 0
        if (range.right < viewport.right) {
            dx = range.right - viewport.right
        }
        if (range.bottom < viewport.bottom) {
            dy = range.bottom - viewport.bottom
        }
        smoothScrollBy(dx, dy)
    }

    fun smoothScrollBy(dx: Int, dy: Int) {
        scroller.startScroll(scrollX, scrollY, dx, dy)
        postInvalidateOnAnimation()
    }

    private val scroller = Scroller(context)

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            scroller.forceFinished(true)
            scrollBy(distanceX.toInt(), distanceY.toInt())
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            val bounds = getScrollRange()
            scroller.forceFinished(true)
            scroller.fling(scrollX, scrollY, -velocityX.toInt(), -velocityY.toInt(),
                    bounds.left, bounds.right-width,
                    bounds.top, bounds.bottom-height)
            postInvalidateOnAnimation()
            return true
        }
    })

    private companion object {
        /** how far two fingers move for each mouse wheel click */
        const val SCROLL_STEP_DP = 24f
        /** how much faster than the finger the pointer moves */
        const val POINTER_SPEED = 1.5f
    }
}
