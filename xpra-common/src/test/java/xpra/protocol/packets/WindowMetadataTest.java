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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import xpra.protocol.data.SizeConstraints;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WindowMetadataTest {

    @Test
    public void testSizeConstraintsWithoutGravity() {
        // as sent for xev: only a minimum size
        final SizeConstraints constraints = metadata(Collections.<String, Object>singletonMap(
            "minimum-size", Arrays.asList(78, 78))).getSizeConstraints();
        assertEquals(SizeConstraints.GRAVITY_NORTH_WEST, constraints.gravity);
        assertEquals(78, constraints.minimumWidth);
        assertEquals(78, constraints.minimumHeight);
    }

    @Test
    public void testSizeConstraintsWithoutMinimumSize() {
        final SizeConstraints constraints = metadata(Collections.<String, Object>singletonMap(
            "gravity", 5)).getSizeConstraints();
        assertEquals(5, constraints.gravity);
        assertEquals(0, constraints.minimumWidth);
    }

    @Test
    public void testNoSizeConstraints() {
        assertNull(new WindowMetadata(1, new HashMap<>()).getSizeConstraints());
    }

    private static WindowMetadata metadata(Map<String, Object> sizeConstraints) {
        final Map<String, Object> meta = new HashMap<>();
        meta.put("size-constraints", sizeConstraints);
        return new WindowMetadata(1, meta);
    }
}
