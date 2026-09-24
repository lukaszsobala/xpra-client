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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import xpra.protocol.IOPacket;

/**
 * The clipboard packets ("clipboard-token", "clipboard-request", "clipboard-contents"...),
 * kept as their list of fields: their layout depends on the packet type, see
 * {@link xpra.client.ClipboardSync}.
 */
public class ClipboardPacket extends IOPacket {

    private final List<Object> fields = new ArrayList<>();

    public ClipboardPacket(String type, Object... fields) {
        super(type);
        this.fields.addAll(Arrays.asList(fields));
    }

    @Override
    protected void deserialize(Iterator<Object> iter) {
        super.deserialize(iter);
        while (iter.hasNext()) {
            fields.add(iter.next());
        }
    }

    @Override
    protected void serialize(Collection<Object> elems) {
        elems.addAll(fields);
    }

    public int size() {
        return fields.size();
    }

    public Object get(int index) {
        return index < fields.size() ? fields.get(index) : null;
    }

    public String getString(int index) {
        final Object value = get(index);
        return value != null ? asString(value) : null;
    }

    public long getLong(int index) {
        return asLong(get(index));
    }

    public byte[] getBytes(int index) {
        final Object value = get(index);
        return value != null ? asByteArray(value) : null;
    }

    public List<String> getStrings(int index) {
        final Object value = get(index);
        return value instanceof List ? asStringList(value) : new ArrayList<>();
    }

    @Override
    public String toString() {
        return type + fields;
    }
}
