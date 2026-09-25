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

package xpra.protocol.packets;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import xpra.protocol.PictureEncoding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HelloRequestTest {

    private static final PictureEncoding[] PICTURES = {PictureEncoding.png, PictureEncoding.jpeg, PictureEncoding.rgb24};

    @SuppressWarnings("unchecked")
    private static Map<String, Object> encodingCaps(HelloRequest hello) {
        return (Map<String, Object>) hello.getCaps().get("encoding");
    }

    @Test
    public void testWithoutVideo() {
        HelloRequest hello = new HelloRequest(1280, 800, null, PictureEncoding.jpeg, PICTURES);
        hello.setVideo(Collections.emptyMap(), null);
        Map<String, Object> encoding = encodingCaps(hello);
        assertEquals("jpeg", encoding.get("setting"));
        assertFalse(((List<?>) encoding.get("core")).contains("h264"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testVideo() {
        HelloRequest hello = new HelloRequest(1280, 800, null, PictureEncoding.jpeg, PICTURES);
        Map<String, Map<String, Object>> video = new LinkedHashMap<>();
        video.put("av1", Collections.singletonMap("score-delta", 70));
        video.put("h264", Collections.singletonMap("score-delta", 60));
        hello.setVideo(video, new int[]{4096, 2160});
        Map<String, Object> encoding = encodingCaps(hello);
        // the server chooses: pictures, or video for what changes a lot
        assertEquals("auto", encoding.get("setting"));
        List<String> core = (List<String>) encoding.get("core");
        assertTrue(core.contains("jpeg"));
        assertTrue(core.contains("av1"));
        assertTrue(core.contains("h264"));
        assertTrue(((List<String>) encoding.get("options")).contains("h264"));
        Map<String, Object> csc = (Map<String, Object>) encoding.get("full_csc_modes");
        assertEquals(Collections.singletonList("YUV420P"), csc.get("h264"));
        assertTrue(csc.containsKey("jpeg"));
        assertEquals(Collections.singletonMap("score-delta", 60), encoding.get("h264"));
        assertEquals(Collections.emptyList(), encoding.get("video_b_frames"));
        assertEquals(java.util.Arrays.asList(4096, 2160), encoding.get("video_max_size"));
    }

    @Test
    public void testChosenVideoEncoding() {
        HelloRequest hello = new HelloRequest(1280, 800, null, PictureEncoding.h264, PICTURES);
        hello.setVideo(Collections.singletonMap("h264", Collections.singletonMap("score-delta", 60)), null);
        assertEquals("h264", encodingCaps(hello).get("setting"));
    }
}
