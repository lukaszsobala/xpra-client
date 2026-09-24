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

package xpra.client;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * An application from the server's menu, ie: from its ".desktop" files.
 */
public final class ServerApp {

    /**
     * The field codes of the "Exec" key, which are replaced by files or URLs to open.
     * See: https://specifications.freedesktop.org/desktop-entry-spec/latest/exec-variables.html
     */
    private static final Pattern FIELD_CODES = Pattern.compile("%[fFuUdDnNickvm]");

    public final String name;
    public final String category;
    public final String command;
    /** The icon, as sent by the server: most likely a PNG or an SVG image, or null. */
    public final byte[] iconData;
    /** The icon format: "png", "svg", "jpg"... or null. */
    public final String iconType;
    /** The "StartupWMClass", which the windows of the application use, or null. */
    public final String wmClass;

    public ServerApp(String name, String category, String command, byte[] iconData, String iconType, String wmClass) {
        this.name = name;
        this.category = category;
        this.command = command;
        this.iconData = iconData;
        this.iconType = iconType;
        this.wmClass = wmClass;
    }

    /**
     * The name of the program, ie: "geany" for "/usr/bin/geany --new-instance".
     */
    public String getProgramName() {
        return programName(command);
    }

    static String programName(String command) {
        String program = command.trim().split("\\s+", 2)[0];
        return program.substring(program.lastIndexOf('/') + 1);
    }

    /**
     * Whether a window most likely belongs to this application, from its WM_CLASS.
     *
     * @param windowClasses - the instance and class names of the window
     */
    public boolean matchesWindow(List<String> windowClasses) {
        return matchesWindow(command, wmClass, windowClasses);
    }

    public static boolean matchesWindow(String command, String wmClass, List<String> windowClasses) {
        final String program = programName(command);
        for (String windowClass : windowClasses) {
            if (windowClass == null || windowClass.isEmpty()) {
                continue;
            }
            if (windowClass.equalsIgnoreCase(program) || windowClass.equalsIgnoreCase(wmClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the field codes, which are only meaningful to file managers, from an "Exec" command.
     */
    public static String cleanCommand(String exec) {
        String command = FIELD_CODES.matcher(exec).replaceAll("").replace("%%", "%");
        return command.trim().replaceAll("\\s{2,}", " ");
    }

    /**
     * Reads the applications from the menu data sent by the server:
     * <pre>{category: {"Name": ..., "Entries": {app name: {"command": ..., "IconData": ..., "IconType": ...}}}}</pre>
     *
     * @return the applications sorted by name, without duplicates
     */
    public static List<ServerApp> fromMenu(Object menu) {
        final List<ServerApp> apps = new ArrayList<>();
        if (!(menu instanceof Map)) {
            return apps;
        }
        for (Map.Entry<?, ?> category : ((Map<?, ?>) menu).entrySet()) {
            if (!(category.getValue() instanceof Map)) {
                continue;
            }
            final Map<?, ?> categoryProps = (Map<?, ?>) category.getValue();
            final Object entries = get(categoryProps, "Entries");
            if (!(entries instanceof Map)) {
                continue;
            }
            final String categoryName = str(category.getKey());
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) entries).entrySet()) {
                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }
                final Map<?, ?> props = (Map<?, ?>) entry.getValue();
                String exec = str(get(props, "command"));
                if (exec == null || exec.isEmpty()) {
                    exec = str(get(props, "Exec"));
                }
                final String name = str(entry.getKey());
                if (name == null || name.isEmpty() || exec == null) {
                    continue;
                }
                final String command = cleanCommand(exec);
                if (command.isEmpty() || contains(apps, name, command)) {
                    continue;
                }
                final Object iconData = get(props, "IconData");
                apps.add(new ServerApp(name, categoryName, command,
                    iconData instanceof byte[] ? (byte[]) iconData : null,
                    str(get(props, "IconType")), str(get(props, "StartupWMClass"))));
            }
        }
        Collections.sort(apps, Comparator.comparing(app -> app.name.toLowerCase(Locale.ROOT)));
        return apps;
    }

    private static boolean contains(List<ServerApp> apps, String name, String command) {
        for (ServerApp app : apps) {
            if (app.name.equals(name) && app.command.equals(command)) {
                return true;
            }
        }
        return false;
    }

    private static Object get(Map<?, ?> map, String key) {
        return map.get(key);
    }

    private static String str(Object value) {
        if (value instanceof byte[]) {
            return new String((byte[]) value, StandardCharsets.UTF_8);
        }
        return value != null ? value.toString() : null;
    }
}
