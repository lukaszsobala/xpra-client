package xpra.protocol.packets;

import java.util.Collection;

public class MapWindow extends WindowPacket {

	private int x;
	private int y;
	private int width;
	private int height;

	public MapWindow(int windowId, int x, int y, int width, int height) {
		super("map-window");
		this.windowId = windowId;
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}
	
	@Override
	public void serialize(Collection<Object> elems) {
		super.serialize(elems);
		elems.add(x);
		elems.add(y);
		elems.add(width);
		elems.add(height);
		if (maximized) {
			// the client properties, then the window state:
			elems.add(java.util.Collections.emptyMap());
			elems.add(java.util.Collections.singletonMap("maximized", true));
		}
	}

	/**
	 * Tells the server that the window fills the screen, so that applications drawing their own
	 * frame, ie: VS Code, do not draw the border they draw around windows that are not maximized.
	 */
	public MapWindow setMaximized(boolean maximized) {
		this.maximized = maximized;
		return this;
	}

	private boolean maximized;

}
