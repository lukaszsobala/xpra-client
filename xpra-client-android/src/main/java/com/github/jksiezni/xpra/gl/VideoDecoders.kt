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

import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import timber.log.Timber
import xpra.protocol.PictureEncoding

/**
 * The video decoders of the device, and the video encodings to ask the server for.
 *
 * Hardware decoders are preferred: AV1, then H.265, then H.264, then VP9 and VP8. Only
 * hardware decoders are used for the encodings other than H.264, which is always accepted, as
 * Android always has a (software) decoder for it. The server then chooses among the encodings
 * of this client and its own encoders, some of them in hardware, for each window.
 */
object VideoDecoders {

    class Decoder(
        val encoding: PictureEncoding,
        val mime: String,
        val name: String,
        val hardware: Boolean,
        val capabilities: MediaCodecInfo.CodecCapabilities
    ) {
        val maxWidth: Int get() = capabilities.videoCapabilities?.supportedWidths?.upper ?: 1920
        val maxHeight: Int get() = capabilities.videoCapabilities?.supportedHeights?.upper ?: 1080
    }

    /**
     * The encodings, from the most preferred, with the MIME type of their decoders, and how much
     * the server should prefer them when decoded in hardware (its "score-delta"): the server
     * scores its encoders from their speed and quality, which already favours its hardware
     * encoders. Without these, it rather sends JPEG pictures for smaller windows.
     */
    private val PREFERENCES = listOf(
        Triple(PictureEncoding.av1, "video/av01", 70),
        Triple(PictureEncoding.h265, "video/hevc", 65),
        Triple(PictureEncoding.hevc, "video/hevc", 65),
        Triple(PictureEncoding.h264, "video/avc", 60),
        Triple(PictureEncoding.vp9, "video/x-vnd.on2.vp9", 50),
        Triple(PictureEncoding.vp8, "video/x-vnd.on2.vp8", 45),
    )

    /** the preference of H.264 when decoded in software: only when clearly better */
    private const val SOFTWARE_H264_DELTA = 20

    /** the best decoder of each encoding */
    val decoders: Map<PictureEncoding, Decoder> by lazy { findDecoders() }

    private fun findDecoders(): Map<PictureEncoding, Decoder> {
        val infos = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
        } catch (e: RuntimeException) {
            Timber.w(e, "Cannot list the video decoders")
            return emptyMap()
        }
        val found = LinkedHashMap<PictureEncoding, Decoder>()
        for ((encoding, mime, _) in PREFERENCES) {
            val candidates = infos.filter { info -> info.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
                .mapNotNull { info ->
                    try {
                        Decoder(encoding, mime, info.name, isHardware(info), info.getCapabilitiesForType(mime))
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }
            // hardware first, then the order of the list, which is the device's preference:
            val best = candidates.firstOrNull { it.hardware } ?: candidates.firstOrNull()
            if (best != null) {
                found[encoding] = best
            }
        }
        Timber.i("Video decoders: %s", found.values.joinToString { "${it.encoding}=${it.name}(${if (it.hardware) "hw" else "sw"})" })
        return found
    }

    private fun isHardware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isHardwareAccelerated
        }
        val name = info.name.lowercase()
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw."))
    }

    /**
     * The encodings to use: those decoded in hardware, and H.264 in any case.
     */
    fun usable(): List<Decoder> = PREFERENCES.mapNotNull { (encoding, _, _) ->
        decoders[encoding]?.takeIf { it.hardware || encoding == PictureEncoding.h264 }
    }

    /**
     * The video encodings to ask for in the hello, with their options.
     */
    fun helloEncodings(): Map<String, Map<String, Any>> {
        val result = LinkedHashMap<String, Map<String, Any>>()
        for (decoder in usable()) {
            val options = LinkedHashMap<String, Any>()
            options["score-delta"] = if (decoder.hardware) {
                PREFERENCES.first { it.first == decoder.encoding }.third
            } else {
                SOFTWARE_H264_DELTA
            }
            if (decoder.encoding == PictureEncoding.h264) {
                options["YUV420P.profile"] = h264Profile(decoder)
            }
            result[decoder.encoding.toString()] = options
        }
        return result
    }

    /**
     * The largest video all the decoders handle.
     */
    fun maxSize(): IntArray? {
        val usable = usable()
        if (usable.isEmpty()) {
            return null
        }
        return intArrayOf(usable.minOf { it.maxWidth }, usable.minOf { it.maxHeight })
    }

    private fun h264Profile(decoder: Decoder): String {
        val profiles = decoder.capabilities.profileLevels.map { it.profile }
        return when {
            CodecProfileLevel.AVCProfileHigh in profiles -> "high"
            CodecProfileLevel.AVCProfileMain in profiles -> "main"
            else -> "constrained-baseline"
        }
    }

    /**
     * ie: "AV1, H.265, H.264" for the settings.
     */
    fun describe(): String = usable()
        .filter { it.encoding != PictureEncoding.hevc }
        .joinToString { decoder ->
            val name = when (decoder.encoding) {
                PictureEncoding.av1 -> "AV1"
                PictureEncoding.h265 -> "H.265"
                PictureEncoding.h264 -> "H.264"
                PictureEncoding.vp9 -> "VP9"
                PictureEncoding.vp8 -> "VP8"
                else -> decoder.encoding.toString()
            }
            if (decoder.hardware) name else "$name (software)"
        }
}
