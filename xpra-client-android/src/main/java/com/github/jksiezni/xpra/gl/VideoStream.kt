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

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.view.Surface
import timber.log.Timber
import xpra.protocol.PictureEncoding
import xpra.protocol.packets.DrawPacket
import java.util.ArrayDeque

/**
 * A video stream of a window, decoded by the device's (hardware) decoder into an external
 * texture, from which [GLComposer] draws the frames into the window.
 *
 * Everything runs on the thread of the GL context: the decoder calls back on its handler.
 */
internal class VideoStream private constructor(
    val encoding: PictureEncoding,
    val width: Int,
    val height: Int,
    /** the external (OES) texture which receives the frames */
    val texture: Int,
    val surfaceTexture: SurfaceTexture,
    private val surface: Surface,
    private val codec: MediaCodec
) {

    private class Frame(val pts: Long, val packet: DrawPacket, val receivedNs: Long)

    private val freeInputs = ArrayDeque<Int>()
    private val waiting = ArrayDeque<Frame>()
    private val decoding = ArrayDeque<Frame>()
    private var nextPts = 0L
    private var released = false

    var onError: ((VideoStream, Exception) -> Unit)? = null

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            freeInputs.add(index)
            feed()
        }

        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
            if (released) {
                return
            }
            // rendered to the surface texture, which then calls onFrameAvailable
            codec.releaseOutputBuffer(index, info.size > 0)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            fail(e)
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Timber.v("video output format: %s", format)
        }
    }

    fun decode(packet: DrawPacket) {
        waiting.add(Frame(nextPts, packet, SystemClock.elapsedRealtimeNanos()))
        // the timestamps only tell the frames apart: see takeShown()
        nextPts += FRAME_PTS_STEP
        feed()
    }

    private fun feed() {
        while (!released && freeInputs.isNotEmpty() && waiting.isNotEmpty()) {
            val frame = waiting.poll()!!
            val index = freeInputs.poll()!!
            try {
                val buffer = codec.getInputBuffer(index) ?: throw IllegalStateException("no input buffer")
                val data = frame.packet.data
                if (data.size > buffer.capacity()) {
                    throw IllegalStateException("video frame too large: ${data.size} > ${buffer.capacity()}")
                }
                buffer.clear()
                buffer.put(data)
                codec.queueInputBuffer(index, 0, data.size, frame.pts, 0)
                // only once queued: a frame which failed is still waiting, see takePending()
                decoding.add(frame)
            } catch (e: Exception) {
                waiting.addFirst(frame)
                fail(e)
                return
            }
        }
    }

    private fun fail(e: Exception) {
        if (!released) {
            Timber.w(e, "Video decoding failed")
            onError?.invoke(this, e)
        }
    }

    /**
     * The frames up to the one now in the surface texture, with their decoding time in
     * microseconds: some frames may be skipped when several are decoded at once.
     */
    fun takeShown(timestampNs: Long): List<Pair<DrawPacket, Long>> {
        val pts = timestampNs / 1000
        val now = SystemClock.elapsedRealtimeNanos()
        val shown = ArrayList<Pair<DrawPacket, Long>>()
        while (decoding.isNotEmpty() && (decoding.peek()!!.pts <= pts || shown.isEmpty())) {
            val frame = decoding.poll()!!
            shown.add(frame.packet to ((now - frame.receivedNs) / 1000).coerceAtLeast(1))
        }
        return shown
    }

    /**
     * The frames not shown yet.
     */
    fun takePending(): List<DrawPacket> {
        val pending = decoding.map { it.packet } + waiting.map { it.packet }
        decoding.clear()
        waiting.clear()
        return pending
    }

    fun release() {
        if (released) {
            return
        }
        released = true
        try {
            codec.stop()
        } catch (e: Exception) {
            Timber.w(e, "Cannot stop the video decoder")
        }
        codec.release()
        surfaceTexture.setOnFrameAvailableListener(null)
        surface.release()
        surfaceTexture.release()
        GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
    }

    companion object {
        private const val FRAME_PTS_STEP = 16_666L

        /**
         * Starts decoding a stream, on the thread of the GL context.
         *
         * @return the stream, or null if the device cannot decode it
         */
        fun create(encoding: PictureEncoding, width: Int, height: Int, fullRange: Boolean?, handler: Handler,
                   onFrame: (VideoStream) -> Unit): VideoStream? {
            val decoder = VideoDecoders.decoders[encoding] ?: return null
            val video = decoder.capabilities.videoCapabilities
            if (video != null && !video.isSizeSupported(width, height)) {
                Timber.w("%s cannot decode %dx%d", decoder.name, width, height)
                return null
            }
            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            val texture = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val surfaceTexture = SurfaceTexture(texture)
            surfaceTexture.setDefaultBufferSize(width, height)
            val surface = Surface(surfaceTexture)
            var codec: MediaCodec? = null
            try {
                codec = MediaCodec.createByCodecName(decoder.name)
                val stream = VideoStream(encoding, width, height, texture, surfaceTexture, surface, codec)
                codec.setCallback(stream.callback, handler)
                val format = MediaFormat.createVideoFormat(decoder.mime, width, height)
                // keyframes can be large:
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxOf(width * height * 3 / 2, 256 * 1024))
                // for the streams which do not describe their colours:
                if (fullRange != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    format.setInteger(MediaFormat.KEY_COLOR_RANGE,
                        if (fullRange) MediaFormat.COLOR_RANGE_FULL else MediaFormat.COLOR_RANGE_LIMITED)
                }
                // a remote desktop wants each frame as soon as possible:
                format.setInteger(MediaFormat.KEY_PRIORITY, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    decoder.capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)) {
                    format.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
                codec.configure(format, surface, null, 0)
                surfaceTexture.setOnFrameAvailableListener({ onFrame(stream) }, handler)
                codec.start()
                Timber.i("Decoding %s %dx%d with %s", encoding, width, height, decoder.name)
                return stream
            } catch (e: Exception) {
                Timber.e(e, "Cannot start the %s decoder %s", encoding, decoder.name)
                codec?.release()
                surface.release()
                surfaceTexture.release()
                GLES20.glDeleteTextures(1, textures, 0)
                return null
            }
        }
    }
}
