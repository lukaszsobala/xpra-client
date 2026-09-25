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

package xpra.protocol;

public enum PictureEncoding {
    rgb24,
    rgb32,
    premult_argb32,
    jpeg,
    png,
    pngP("png/P"),
    pngL("png/L"),
    // video, see isVideo():
    h264,
    h265,
    hevc,
    av1,
    vp8,
    vp9;

    private static final PictureEncoding[] values = values();
    private final String code;

    PictureEncoding() {
        code = name();
    }

    PictureEncoding(String code) {
        this.code = code;
    }

    /**
     * Whether the pictures are the frames of a video stream, which depend on the previous ones.
     */
    public boolean isVideo() {
        switch (this) {
            case h264:
            case h265:
            case hevc:
            case av1:
            case vp8:
            case vp9:
                return true;
            default:
                return false;
        }
    }

    @Override
    public String toString() {
        return code;
    }

    public static PictureEncoding decode(String code) {
        for (PictureEncoding encoding : values) {
            if (encoding.code.equals(code)) {
                return encoding;
            }
        }
        throw new IllegalArgumentException("Unsupported picture encoding: " + code);
    }

    public static String[] toString(PictureEncoding[] encodings) {
        final String[] array = new String[encodings.length];
        for (int i = 0; i < encodings.length; ++i) {
            array[i] = encodings[i].toString();
        }
        return array;
    }

}
