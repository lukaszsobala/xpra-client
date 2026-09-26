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

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import xpra.protocol.XpraSender;
import xpra.protocol.data.SizeConstraints;
import xpra.protocol.packets.CloseWindow;
import xpra.protocol.packets.ConfigureWindow;
import xpra.protocol.packets.DamageSequence;
import xpra.protocol.packets.DrawPacket;
import xpra.protocol.packets.FocusRequest;
import xpra.protocol.packets.BufferRefresh;
import xpra.protocol.packets.KeyAction;
import xpra.protocol.packets.MapWindow;
import xpra.protocol.packets.MouseButtonAction;
import xpra.protocol.packets.NewWindow;
import xpra.protocol.packets.PointerPosition;
import xpra.protocol.packets.UnmapWindow;
import xpra.protocol.packets.WindowIcon;
import xpra.protocol.packets.WindowMetadata;

public abstract class XpraWindow {

	private final int id;
	private final int parentId;

	private final boolean overrideRedirect;

	private int x;
	private int y;
	private int width;
	private int height;
	private int minimumWidth = 0;
	private int minimumHeight = 0;

	private boolean mapped;

    private XpraSender sender;

    private String title;

    private List<String> windowClasses = Collections.emptyList();
    private String command;

	public XpraWindow(NewWindow wndPacket) {
		this.id = wndPacket.getWindowId();
		this.x = wndPacket.getX();
		this.y = wndPacket.getY();
		this.width = wndPacket.getWidth();
		this.height = wndPacket.getHeight();
		this.parentId = wndPacket.getMetadata().getParentId();
		this.title = wndPacket.getMetadata().getTitle();
		this.overrideRedirect = wndPacket.isOverrideRedirect();

        SizeConstraints sizeConstraints = wndPacket.getMetadata().getSizeConstraints();
        if (sizeConstraints != null) {
            this.minimumWidth = sizeConstraints.minimumWidth;
            this.minimumHeight = sizeConstraints.minimumHeight;
        }
	}
	
	void setSender(XpraSender sender) {
		this.sender = sender;
	}
	
	public int getId() {
		return id;
	}
	
	public int getParentId() {
		return parentId;
	}
	
	public boolean hasParent() {
		return parentId != WindowMetadata.NO_PARENT;
	}

    public String getTitle() {
        return title;
    }

    /**
     * A name for the window which means something to the user: its title, unless the title is
     * missing, or only the name of a script or program (ie: "app.js", which apps written for
     * Node.js often show): then the name of its application, from the server's menu, or else
     * from its WM_CLASS or command.
     *
     * @param apps - the applications of the server, see {@link XpraClient#getServerApps()}
     * @return the title, even a technical one, when nothing better is known, or null without one
     */
    public String getLabel(List<ServerApp> apps) {
        return label(title, windowClasses, command, apps);
    }

    static String label(String title, List<String> windowClasses, String command, List<ServerApp> apps) {
        final String trimmed = title != null ? title.trim() : "";
        if (!trimmed.isEmpty() && !isTechnical(trimmed, command)) {
            return trimmed;
        }
        for (ServerApp app : apps) {
            if (app.matchesWindow(windowClasses)) {
                return app.name;
            }
        }
        // the class name, ie: "Geany", rather than the instance name
        for (int i = windowClasses.size() - 1; i >= 0; i--) {
            final String windowClass = windowClasses.get(i);
            if (windowClass != null && !windowClass.isEmpty() && !isTechnical(windowClass, command)) {
                return Character.toUpperCase(windowClass.charAt(0)) + windowClass.substring(1);
            }
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final Pattern SCRIPT_OR_PROGRAM =
        Pattern.compile("(?i)\\S*\\.(js|mjs|cjs|ts|py|pyw|sh|bash|pl|rb|php|jar|exe|bin|appimage)");

    /**
     * Whether a title is only the name of a script, ie: "app.js", a path, or the program itself.
     */
    static boolean isTechnical(String title, String command) {
        if (SCRIPT_OR_PROGRAM.matcher(title).matches() || title.startsWith("/")) {
            return true;
        }
        if (command != null && !command.trim().isEmpty()) {
            return title.equals(command.trim()) || title.equals(ServerApp.programName(command));
        }
        return false;
    }

    /**
     * The WM_CLASS of the window: its instance and class names, ie: ["geany", "Geany"].
     */
    public List<String> getWindowClasses() {
        return windowClasses;
    }

    /**
     * The command that started the application of this window (WM_COMMAND), or null.
     */
    public String getCommand() {
        return command;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getMinimumWidth() {
        return minimumWidth;
    }

    public int getMinimumHeight() {
        return minimumHeight;
    }

    public boolean isOverrideRedirect() {
        return overrideRedirect;
    }

    protected void onStart(NewWindow wnd) {
		onMetadataUpdate(wnd.getMetadata());
	}
	
	protected void onStop() {
	    mapped = false;
    }

    /**
     * Called instead of {@link #onStop()} when the connection is lost: the window most likely
     * still exists on the server, and comes back after reconnecting.
     */
    protected void onConnectionLost() {
        onStop();
    }
	
	protected void onMetadataUpdate(WindowMetadata metadata) {
        final String title = metadata.getTitle();
        if (title != null) {
            this.title = title;
        }
        final List<String> classes = metadata.getClassInstance();
        if (classes != null) {
            this.windowClasses = classes;
        }
        final String command = metadata.getAsString("command");
        if (command != null) {
            this.command = command;
        }
        WindowIcon icon = metadata.getIcon();
        if (icon != null) {
            onIconUpdate(icon);
        }
    }

	protected void onMoveResize(ConfigureWindow config) {
		// empty
	}
	
	protected void onIconUpdate(WindowIcon windowIcon) {
        // empty
	}

	public abstract void onDraw(DrawPacket packet);

	protected void sendDamageSequence(DrawPacket packet, long frameTime) {
		sendDamageSequence(sender, packet, frameTime);
	}

	public static void sendDamageSequence(XpraSender sender, DrawPacket packet, long frameTime) {
		if (sender != null && packet.packet_sequence >= 0) {
			sender.send(new DamageSequence(packet, frameTime));
		}
	}

	protected void setFocused(boolean focused) {
		if(focused) {
			sender.send(new FocusRequest(id));
		} else {
			sender.send(new FocusRequest(0));
		}
	}

	protected void mapWindow(int x, int y, int width, int height) {
		mapWindow(x, y, width, height, false);
	}

	/**
	 * @param maximized true if the window fills the screen
	 */
	protected void mapWindow(int x, int y, int width, int height, boolean maximized) {
	    if (!mapped) {
            sender.send(new MapWindow(id, x, y, width, height).setMaximized(maximized));
            mapped = true;
        }
	}
	
	protected void configureWindow(int x, int y, int width, int height) {
		configureWindow(x, y, width, height, false);
	}

	/**
	 * @param maximized true if the window fills the screen
	 */
	protected void configureWindow(int x, int y, int width, int height, boolean maximized) {
		sender.send(new ConfigureWindow(id, x, y, width, height).setMaximized(maximized));
	}
	
	protected void unmapWindow() {
	    if (mapped) {
            sender.send(new UnmapWindow(id));
            mapped = false;
        }
	}

    public boolean isShown() {
        return mapped;
    }

    /**
     * Asks the server to send the whole window again, ie: after its contents were lost.
     */
    public void requestRefresh() {
        if (sender != null) {
            sender.send(new BufferRefresh(id));
        }
    }

    protected void closeWindow() {
		sender.send(new CloseWindow(id));
	}
	
	public void movePointer(int x, int y) {
		sender.send(new PointerPosition(id, x, y));
	}
	
	public void mouseAction(int button, boolean pressed, int x, int y) {
		if(x < 0 || y < 0) {
			throw new IllegalArgumentException("Negative coordinates are not allowed: " + x + ", " + y);
		}
		sender.send(new MouseButtonAction(id, button, pressed, x, y));
	}
	
	public void keyboardAction(int keycode, String keyname, boolean pressed) {
		sender.send(new KeyAction(id, keycode, keyname, pressed));
	}

	/**
	 * @param modifiers the modifiers held down, ie: "shift" or "control"
	 * @param string the text produced by the key, if any
	 */
	public void keyboardAction(int keycode, String keyname, boolean pressed, java.util.List<String> modifiers, String string) {
		sender.send(new KeyAction(id, keycode, keyname, pressed, modifiers, string));
	}

	/**
	 * Sends a new keyboard map to the server.
	 */
	public void keymapChanged(java.util.Map<String, Object> keymap) {
		sender.send(new xpra.protocol.packets.KeymapChanged(keymap));
	}
}
