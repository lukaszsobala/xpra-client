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

import android.opengl.GLES20
import com.android.grafika.gles.EglSurfaceBase
import com.android.grafika.gles.GlUtil
import com.android.grafika.gles.WindowSurface
import timber.log.Timber

/**
 *
 */
internal class GLDrawTarget(
        private var eglSurface: EglSurfaceBase,
        val texture: Int
        ) {

    private var textureWidth: Int = 0
    private var textureHeight: Int = 0


    /**
     * The texture holds the whole window, as it is stretched over the whole surface when rendered.
     * Resizing it discards its contents, so it should match the window size, which the server sends
     * with every update, rather than grow with the area of each update.
     *
     * @param windowSize the window size sent with the update, if known
     * @param right the right edge of the update
     * @param bottom the bottom edge of the update
     * @return true if the texture was (re)created, so its previous contents are lost
     */
    fun validateTextureSize(windowSize: IntArray?, right: Int, bottom: Int): Boolean {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        var width = maxOf(textureWidth, right)
        var height = maxOf(textureHeight, bottom)
        if (windowSize != null && windowSize[0] > 0 && windowSize[1] > 0) {
            width = maxOf(windowSize[0], right)
            height = maxOf(windowSize[1], bottom)
        }
        if (width != textureWidth || height != textureHeight) {
            Timber.d("create texture ${width}x${height}")
            // OpenGL ES 2 requires updates to use the texture's format: Android bitmaps are RGBA,
            // so the texture is RGBA too and "rgb" pixels are converted to RGBA before uploading
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GlUtil.checkGlError("glTexImage2D")
            textureWidth = width
            textureHeight = height
            return true
        }
        return false
    }

    fun coversTexture(x: Int, y: Int, width: Int, height: Int): Boolean =
        x <= 0 && y <= 0 && x + width >= textureWidth && y + height >= textureHeight

    fun makeCurrent() {
        eglSurface.makeCurrent()
        if (eglSurface is WindowSurface) {
            GLES20.glViewport(0, 0, eglSurface.width, eglSurface.height)
        }
    }

    fun swapBuffers() {
        eglSurface.swapBuffers()
        GlUtil.checkGlError("sswapBuffers")
    }

    fun setTarget(eglSurface: EglSurfaceBase) {
        if (this.eglSurface is WindowSurface) {
            this.eglSurface.releaseEglSurface()
        }
        this.eglSurface = eglSurface
    }

    fun isEglSurface(surface: EglSurfaceBase) : Boolean = eglSurface == surface

}
