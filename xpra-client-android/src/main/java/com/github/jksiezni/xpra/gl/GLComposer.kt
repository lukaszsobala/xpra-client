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

package com.github.jksiezni.xpra.gl

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.android.grafika.gles.*
import timber.log.Timber
import xpra.protocol.PictureEncoding
import xpra.protocol.packets.DrawPacket
import xpra.video.H264Headers
import java.nio.ByteBuffer

/**
 *
 */
/**
 * @param onContentLost called when the contents of a window were lost and must be sent again
 */
class GLComposer(
    private val callback: ComposeCallback,
    private val onContentLost: (windowId: Int) -> Unit
) : GLThread() {

    private val drawTargets: MutableMap<Int, GLDrawTarget> = mutableMapOf()

    /** the video stream of each window, if any */
    private val videoStreams: MutableMap<Int, VideoStream> = mutableMapOf()
    private val videoMatrix = FloatArray(16)
    /** draws the video frames into the textures of the windows */
    private var framebuffer = 0

    private val baseSurface: EglSurfaceBase by lazy {
        if (eglCore.supportsSurfacelessContext()) {
            return@lazy EglSurfaceBase(eglCore)
        } else {
            return@lazy OffscreenSurface(eglCore, 2, 2)
        }
    }

    private val handler: Handler by lazyHandler(this) { msg ->
        when (msg.what) {
            MSG_CREATE_DRAW_TARGET -> {
                val windowId = msg.arg1
                Timber.v("MSG_CREATE_DRAW_TARGET $windowId")
                drawTargets[windowId] = GLDrawTarget(baseSurface, frameRect.createTextureObject())
            }
            MSG_REMOVE_DRAW_TARGET -> {
                val windowId = msg.arg1
                Timber.v("MSG_REMOVE_DRAW_TARGET $windowId")
                baseSurface.makeCurrent()
                drawTargets.remove(windowId)
                closeVideo(windowId)
            }
            MSG_ADD_SURFACE_TEX -> {
                val windowId = msg.arg1
                val surfaceTexture = msg.obj as SurfaceTexture
                Timber.v("MSG_ADD_SURFACE_TEX $windowId")
                drawTargets[windowId]?.let {
                    it.setTarget(WindowSurface(eglCore, surfaceTexture))
                    it.makeCurrent()
                    render(it)
                }

            }
            MSG_DEL_SURFACE_TEX -> {
                val windowId = msg.arg1
                val surfaceTexture = msg.obj as SurfaceTexture
                Timber.v("MSG_DEL_SURFACE_TEX $windowId")
                baseSurface.makeCurrent()
                drawTargets[windowId]?.setTarget(baseSurface)
                surfaceTexture.release()
            }
            MSG_DRAW_PACKET -> {
                val packet = msg.obj as DrawPacket
                composePacket(packet)
            }
        }
        true
    }

    private lateinit var frameRect: FullFrameRect
    private lateinit var videoFrameRect: FullFrameRect

    init {
        start()
    }

    override fun onSetupGL(eglCore: EglCore) {
        baseSurface.makeCurrent()
        val program = Texture2dProgram.create(Texture2dProgram.ProgramType.TEXTURE_2D)
        frameRect = FullFrameRect(program)
        videoFrameRect = FullFrameRect(Texture2dProgram.create(Texture2dProgram.ProgramType.TEXTURE_EXT))
    }

    override fun onDestroyGL(eglCore: EglCore) {
        videoStreams.values.forEach { it.release() }
        videoStreams.clear()
        eglCore.makeNothingCurrent()
        baseSurface.releaseEglSurface()
    }

    fun createDrawingTarget(windowId: Int) {
        handler.obtainMessage(MSG_CREATE_DRAW_TARGET, windowId, 0).sendToTarget()
    }

    fun destroyDrawingTarget(windowId: Int) {
        handler.obtainMessage(MSG_REMOVE_DRAW_TARGET, windowId, 0).sendToTarget()
    }

    fun queueToDraw(packet: DrawPacket) {
        handler.obtainMessage(MSG_DRAW_PACKET, packet).sendToTarget()
    }

    fun addSurface(windowId: Int, surfaceTexture: SurfaceTexture) {
        handler.obtainMessage(MSG_ADD_SURFACE_TEX, windowId, 0, surfaceTexture).sendToTarget()
    }

    fun removeSurface(windowId: Int, surfaceTexture: SurfaceTexture) {
        handler.obtainMessage(MSG_DEL_SURFACE_TEX, windowId, 0, surfaceTexture).sendToTarget()
    }

    private fun composePacket(packet: DrawPacket) {
        if (packet.encoding.isVideo) {
            decodeVideo(packet)
            return
        }
        val glWindow = drawTargets[packet.windowId]
        if (glWindow != null) {
            // process packet
            val startTime = SystemClock.elapsedRealtimeNanos()
            glWindow.makeCurrent()
            if (glWindow.validateTextureSize(packet.windowSize, packet.x + packet.w, packet.y + packet.h)
                && !glWindow.coversTexture(packet.x, packet.y, packet.w, packet.h)) {
                // this update only redraws a part of the new texture, ask for the rest of the window
                Timber.d("Requesting a refresh of window %d", packet.windowId)
                onContentLost(packet.windowId)
            }
            try {
                composeImage(glWindow.texture, packet)
            } catch (e: Exception) {
                // a negative decode time tells the server that this update failed
                Timber.e(e, "Failed to draw %s", packet)
                callback.onComposed(packet, -1)
                return
            }
            render(glWindow)
            callback.onComposed(packet, elapsedMicros(startTime))
        } else {
            Timber.w("No surface to compose a drawing for window id=%d", packet.windowId)
        }
    }

    /**
     * Decodes a frame of a video stream: it is drawn once decoded, see [onVideoFrame].
     */
    private fun decodeVideo(packet: DrawPacket) {
        val windowId = packet.windowId
        val glWindow = drawTargets[windowId]
        if (glWindow == null) {
            Timber.w("No surface to decode a video frame for window id=%d", windowId)
            return
        }
        glWindow.makeCurrent()
        val size = packet.videoSize
        var stream = videoStreams[windowId]
        if (stream != null && (stream.encoding != packet.encoding || stream.width != size[0] ||
                stream.height != size[1] || packet.frame == 0)) {
            // a new stream: the previous one ended
            closeVideo(windowId)
            stream = null
        }
        if (stream == null) {
            if (packet.frame > 0) {
                // the middle of a stream cannot be decoded: the server starts a new one
                callback.onComposed(packet, DECODE_ERROR)
                return
            }
            stream = VideoStream.create(packet.encoding, size[0], size[1], fullRange(packet), handler) {
                onVideoFrame(windowId, it)
            }
            if (stream == null) {
                callback.onComposed(packet, DECODE_ERROR)
                return
            }
            stream.onError = { failed, _ -> onVideoError(windowId, failed) }
            videoStreams[windowId] = stream
        }
        val fullRange = fullRange(packet)
        if (packet.encoding == PictureEncoding.h264 && fullRange != null) {
            // the servers' x264 describes the colours wrongly: see H264Headers
            packet.data = H264Headers.fixColours(packet.data, fullRange)
        }
        stream.decode(packet)
    }

    /** whether the frame is in full range, or null if the server does not say */
    private fun fullRange(packet: DrawPacket): Boolean? = packet.options["full-range"] as? Boolean

    private fun onVideoFrame(windowId: Int, stream: VideoStream) {
        if (videoStreams[windowId] !== stream) {
            return
        }
        val glWindow = drawTargets[windowId]
        if (glWindow != null) glWindow.makeCurrent() else baseSurface.makeCurrent()
        stream.surfaceTexture.updateTexImage()
        stream.surfaceTexture.getTransformMatrix(videoMatrix)
        val shown = stream.takeShown(stream.surfaceTexture.timestamp)
        val last = shown.lastOrNull()?.first
        if (glWindow != null && last != null && last.isPainted) {
            try {
                if (glWindow.validateTextureSize(last.windowSize, last.x + last.w, last.y + last.h)
                    && !glWindow.coversTexture(last.x, last.y, last.w, last.h)) {
                    onContentLost(windowId)
                }
                drawVideoFrame(glWindow.texture, last, stream.texture)
                // back to the viewport of the window surface:
                glWindow.makeCurrent()
                render(glWindow)
            } catch (e: Exception) {
                Timber.e(e, "Failed to draw a video frame of window %d", windowId)
                shown.forEach { callback.onComposed(it.first, DECODE_ERROR) }
                closeVideo(windowId)
                return
            }
        }
        shown.forEach { (packet, micros) -> callback.onComposed(packet, micros) }
    }

    /**
     * Stretches the frame, which the server may have scaled down, over the area of the update.
     * The texture of the window has the top of the window in its first row, like the frame
     * rendered with the transform of its surface texture, so the frame is drawn upright.
     */
    private fun drawVideoFrame(windowTexture: Int, packet: DrawPacket, videoTexture: Int) {
        if (framebuffer == 0) {
            val framebuffers = IntArray(1)
            GLES20.glGenFramebuffers(1, framebuffers, 0)
            framebuffer = framebuffers[0]
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        try {
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, windowTexture, 0)
            val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                throw IllegalStateException("incomplete framebuffer: $status")
            }
            GLES20.glViewport(packet.x, packet.y, packet.w, packet.h)
            videoFrameRect.drawFrame(videoTexture, videoMatrix)
            GlUtil.checkGlError("drawVideoFrame")
        } finally {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
    }

    /**
     * Tells the server that the frames failed: it then starts a new stream, with a refresh.
     */
    private fun onVideoError(windowId: Int, stream: VideoStream) {
        if (videoStreams[windowId] === stream) {
            stream.takePending().forEach { callback.onComposed(it, DECODE_ERROR) }
            closeVideo(windowId)
        }
    }

    private fun closeVideo(windowId: Int) {
        videoStreams.remove(windowId)?.let { stream ->
            // the frames of an ended stream, which will never be shown, are not errors:
            stream.takePending().forEach { callback.onComposed(it, 1) }
            stream.release()
        }
    }

    private fun elapsedMicros(startNs: Long): Long =
        ((SystemClock.elapsedRealtimeNanos() - startNs) / 1000).coerceAtLeast(1)

    private fun render(glDrawTarget: GLDrawTarget) {
        if (!glDrawTarget.isEglSurface(baseSurface)) {
            GlUtil.checkGlError("startRender")
            frameRect.drawFrame(glDrawTarget.texture, GlUtil.IDENTITY_MATRIX)
            GlUtil.checkGlError("endRender")
            glDrawTarget.swapBuffers()
        }
    }

    private fun composeImage(tex: Int, packet: DrawPacket) {
        when (packet.encoding) {
            PictureEncoding.png, PictureEncoding.pngL, PictureEncoding.pngP, PictureEncoding.jpeg -> {
                val bitmap = BitmapFactory.decodeByteArray(packet.data, 0, packet.data.size, RGBA_BITMAP)
                    ?: throw IllegalArgumentException("Failed to decode ${packet.encoding} image")
                composeBitmap(tex, bitmap, packet.x, packet.y)
                bitmap.recycle()
            }
            PictureEncoding.rgb24, PictureEncoding.rgb32 -> {
                composeRGBA(tex, packet.readRgbaPixels(), packet.x, packet.y, packet.w, packet.h)
            }
            else -> Timber.e("Unable to draw: %s", packet.encoding)
        }
    }

    private fun composeRGBA(tex: Int, pixels: ByteArray, x: Int, y: Int, width: Int, height: Int) {
        val buffer = ByteBuffer.wrap(pixels)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, x, y, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        GlUtil.checkGlError("texSubImage2D")
    }

    private fun composeBitmap(tex: Int, bitmap: Bitmap, x: Int, y: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
        // uploads as GL_RGBA, which must match the format of the texture
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, x, y, bitmap)
        GlUtil.checkGlError("texSubImage2D")
    }

    fun interface ComposeCallback {
        /**
         * @param decodeTime - in microseconds, or negative for a failure
         */
        fun onComposed(packet: DrawPacket, decodeTime: Long)
    }

    companion object {
        private val RGBA_BITMAP = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }

        /** what the server expects as the decoding time of a frame which failed */
        private const val DECODE_ERROR = -1L

        const val MSG_DRAW_PACKET = 1
        const val MSG_ADD_SURFACE_TEX = 2
        const val MSG_DEL_SURFACE_TEX = 3
        const val MSG_CREATE_DRAW_TARGET = 4
        const val MSG_REMOVE_DRAW_TARGET = 5

        private fun lazyHandler(handlerThread: HandlerThread, callback: Handler.Callback) : Lazy<Handler> {
            return lazy { Handler(handlerThread.looper, callback) }
        }
    }
}