package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/// The full-screen world map - extends the minimap ([KubeUIMinimap])'s own real
/// per-block [net.minecraft.world.level.material.MapColor] sampling into a pannable (mouse drag or
/// arrow keys)/zoomable (scroll wheel) view over [KubeUIExploredMapCache]'s persisted, progressively
/// revealed cells - not just what's within a small fixed radius. `KubeUI.worldMap()` opens it.
///
/// Styled after a real, familiar minimap-mod layout (Journeymap etc. - "fait un peu comme
/// Journeymap", real reported feedback that the old flat/borderless version looked bare): a docked
/// top toolbar, a docked right waypoint sidebar (jump/remove per entry, no separate command needed
/// to manage them), a bordered map viewport with a compass tick and a scale bar, and a live
/// coordinate/biome/zoom readout under the cursor - the same information a player actually looks
/// for on a map, not just the colored cells themselves. Each sidebar entry also has a "Trk" toggle -
/// a real, reported gap: [KubeUIWaypoints#setTracked] existed and
/// [dev.kubeui.plugin.KubeUIRouteHudRenderer] already drew the resulting HUD compass/distance
/// indicator, but nothing anywhere ever actually called `setTracked`, so a placed waypoint was never
/// reachable from the HUD without opening this screen at all. Like Journeymap's own per-waypoint
/// "show on HUD" toggle, a tracked waypoint's direction/distance stays visible top-center of the
/// screen while this map is closed, until toggled off or another waypoint is tracked instead.
///
/// Renders directly (`extractRenderState`, the one real override point a plain `Screen` subclass
/// has, decompiled from [KubeUIMultiWindowHost]) rather than through a declarative
/// `KubeUIScreenBuilder` screen - continuous drag-panning and scroll-zooming don't fit that
/// widget-tree model well, the same reason [KubeUIStorageScreen] stays a plain
/// `AbstractContainerScreen` instead.
public final class KubeUIWorldMapScreen extends Screen {
	private static final int VIEW_RADIUS_CELLS = 48;
	private static final String[] VIEW_DIMENSIONS = {null, null, "minecraft:the_nether", "minecraft:the_end"};
	private static final String[] VIEW_LAYERS = {"surface", "caves", "surface", "surface"};
	private static final String[] VIEW_LABELS = {"Surface", "Caves", "Nether", "End"};

	private static final int TOP_BAR_HEIGHT = 28;
	private static final int SIDEBAR_WIDTH = 140;
	private static final int MARGIN = 6;

	private static final int PANEL_BG = 0xF01A1A1A;
	private static final int PANEL_BORDER = 0xFF3A3A3A;
	private static final int VIEWPORT_BG = 0xFF0A0A0A;
	private static final int INFO_BG = 0xB0101010;
	private static final int PLAYER_COLOR = 0xFF41C7FF;

	private double centerX;
	private double centerZ;
	private double blocksPerPixel = 4.0;
	private int viewIndex = 0;
	private int caveY = 40;

	private final List<AbstractWidget> waypointWidgets = new ArrayList<>();

	public KubeUIWorldMapScreen() {
		super(Component.literal("World Map"));
		var player = Minecraft.getInstance().player;
		this.centerX = player != null ? player.getX() : 0;
		this.centerZ = player != null ? player.getZ() : 0;
	}

	private String currentDimension() {
		var real = VIEW_DIMENSIONS[viewIndex];
		if (real != null) {
			return real;
		}
		var level = Minecraft.getInstance().level;
		return level != null ? level.dimension().location().toString() : "minecraft:overworld";
	}

	private String currentLayer() {
		return VIEW_LAYERS[viewIndex];
	}

	private boolean viewingCurrentPosition() {
		var level = Minecraft.getInstance().level;
		return VIEW_DIMENSIONS[viewIndex] == null && level != null;
	}

	// ---------------------------------------------------------------- layout

	private int mapLeft() {
		return MARGIN;
	}

	private int mapTop() {
		return TOP_BAR_HEIGHT + MARGIN;
	}

	private int mapRight() {
		return this.width - SIDEBAR_WIDTH - MARGIN;
	}

	private int mapBottom() {
		return this.height - MARGIN;
	}

	private double viewportCenterX() {
		return (mapLeft() + mapRight()) / 2.0;
	}

	private double viewportCenterY() {
		return (mapTop() + mapBottom()) / 2.0;
	}

	private boolean insideViewport(double screenX, double screenY) {
		return screenX >= mapLeft() && screenX <= mapRight() && screenY >= mapTop() && screenY <= mapBottom();
	}

	private int worldToScreenX(double worldX) {
		return (int) (viewportCenterX() + (worldX - centerX) / blocksPerPixel);
	}

	private int worldToScreenZ(double worldZ) {
		return (int) (viewportCenterY() + (worldZ - centerZ) / blocksPerPixel);
	}

	private double screenToWorldX(double screenX) {
		return centerX + (screenX - viewportCenterX()) * blocksPerPixel;
	}

	private double screenToWorldZ(double screenY) {
		return centerZ + (screenY - viewportCenterY()) * blocksPerPixel;
	}

	// ---------------------------------------------------------------- widgets

	@Override
	protected void init() {
		super.init();

		addRenderableWidget(Button.builder(Component.literal("Layer: " + VIEW_LABELS[viewIndex]), b -> {
			viewIndex = (viewIndex + 1) % VIEW_LABELS.length;
			b.setMessage(Component.literal("Layer: " + VIEW_LABELS[viewIndex]));
			rebuildWaypointSidebar();
		}).bounds(88, 4, 100, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Add Waypoint Here"), b -> {
			var player = Minecraft.getInstance().player;
			if (player != null) {
				KubeUIWaypoints.add("Waypoint", centerX, player.getY(), centerZ, currentDimension(), 0xFFFFD700);
				rebuildWaypointSidebar();
			}
		}).bounds(196, 4, 130, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(this.width - 68, 4, 60, 20).build());

		rebuildWaypointSidebar();
	}

	/// Rebuilds the right-side waypoint list (jump-to + remove per entry) - only entries in the
	/// currently-viewed dimension, matching what's actually plotted on the map itself. Re-run
	/// whenever the set of relevant waypoints could have changed (layer switch, add, remove) rather
	/// than kept live/dynamic - a world map's waypoint list isn't expected to change from outside
	/// this screen while it's open.
	private void rebuildWaypointSidebar() {
		for (var widget : waypointWidgets) {
			removeWidget(widget);
		}
		waypointWidgets.clear();

		int sidebarX = this.width - SIDEBAR_WIDTH + 8;
		int rowY = TOP_BAR_HEIGHT + MARGIN + 16;
		String dimension = currentDimension();

		for (var waypoint : KubeUIWaypoints.all()) {
			if (!waypoint.dimension().equals(dimension)) {
				continue;
			}
			if (rowY > this.height - 24) {
				break;
			}

			var jump = Button.builder(Component.literal(trimToWidth(waypoint.name(), 72)), b -> {
				centerX = waypoint.x();
				centerZ = waypoint.z();
			}).bounds(sidebarX, rowY, 78, 18).build();

			boolean isTracked = isTracked(waypoint);
			var track = Button.builder(
				Component.literal("Trk").withStyle(style -> style.withColor(isTracked ? 0xFFFFD700 : 0xFFAAAAAA)),
				b -> {
					KubeUIWaypoints.setTracked(isTracked ? null : waypoint.id());
					rebuildWaypointSidebar();
				}
			).bounds(sidebarX + 80, rowY, 24, 18).build();
			track.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
				isTracked ? "Showing on HUD - click to stop" : "Show on HUD (like Journeymap's waypoint tracking)"
			)));

			var remove = Button.builder(Component.literal("x"), b -> {
				KubeUIWaypoints.remove(waypoint.id());
				rebuildWaypointSidebar();
			}).bounds(sidebarX + 106, rowY, 18, 18).build();

			addRenderableWidget(jump);
			addRenderableWidget(track);
			addRenderableWidget(remove);
			waypointWidgets.add(jump);
			waypointWidgets.add(track);
			waypointWidgets.add(remove);
			rowY += 20;
		}
	}

	private boolean isTracked(KubeUIWaypoint waypoint) {
		var tracked = KubeUIWaypoints.tracked();
		return tracked != null && tracked.id().equals(waypoint.id());
	}

	private String trimToWidth(String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String truncated = text;
		while (!truncated.isEmpty() && font.width(truncated + "...") > maxWidth) {
			truncated = truncated.substring(0, truncated.length() - 1);
		}
		return truncated + "...";
	}

	// ---------------------------------------------------------------- rendering

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float delta) {
		var graphics = new GuiGraphicsExtractor(realGraphics);
		graphics.fill(0, 0, width, height, 0xE0101010);

		int mapLeft = mapLeft(), mapTop = mapTop(), mapRight = mapRight(), mapBottom = mapBottom();
		int mapWidth = mapRight - mapLeft, mapHeight = mapBottom - mapTop;

		String dimension = currentDimension();
		String layer = currentLayer();
		int cell = KubeUIExploredMapCache.CELL_SIZE;
		double pixelsPerCell = cell / blocksPerPixel;

		graphics.fill(mapLeft, mapTop, mapRight, mapBottom, VIEWPORT_BG);

		// Live-reveal: while looking at the dimension/layer the player is actually standing in,
		// sample every visible cell (not just the one the player is standing in, unlike the slow
		// background sampler in KubeUIMapClientEvents) so panning near loaded chunks reveals them
		// immediately rather than waiting on the next background tick.
		if (viewingCurrentPosition()) {
			var level = Minecraft.getInstance().level;
			int cellsAcross = (int) (mapWidth / pixelsPerCell) + 2;
			int cellsDown = (int) (mapHeight / pixelsPerCell) + 2;
			int centerCellX = (int) Math.floor(centerX / cell);
			int centerCellZ = (int) Math.floor(centerZ / cell);
			for (int dz = -cellsDown / 2; dz <= cellsDown / 2; dz++) {
				for (int dx = -cellsAcross / 2; dx <= cellsAcross / 2; dx++) {
					KubeUIExploredMapCache.markExplored(level, dimension, layer, (centerCellX + dx) * cell + cell / 2, (centerCellZ + dz) * cell + cell / 2, caveY);
				}
			}
		}

		graphics.enableScissor(mapLeft, mapTop, mapRight, mapBottom);

		int centerCellX = (int) Math.floor(centerX / cell);
		int centerCellZ = (int) Math.floor(centerZ / cell);
		int cellsAcross = (int) (mapWidth / pixelsPerCell) + 2;
		int cellsDown = (int) (mapHeight / pixelsPerCell) + 2;

		for (int dz = -cellsDown / 2 - 1; dz <= cellsDown / 2 + 1; dz++) {
			int cellZ = centerCellZ + dz;
			double screenY = viewportCenterY() + (cellZ * cell - centerZ) / blocksPerPixel;
			for (int dx = -cellsAcross / 2 - 1; dx <= cellsAcross / 2 + 1; dx++) {
				int cellX = centerCellX + dx;
				double screenX = viewportCenterX() + (cellX * cell - centerX) / blocksPerPixel;

				Integer color = KubeUIExploredMapCache.colorOf(dimension, layer, cellX, cellZ);
				if (color == null) {
					continue;
				}
				graphics.fill((int) screenX, (int) screenY, (int) (screenX + pixelsPerCell) + 1, (int) (screenY + pixelsPerCell) + 1, color);
			}
		}

		if (viewingCurrentPosition()) {
			for (var icon : KubeUIMapIcons.collect(Minecraft.getInstance().level, centerX, centerZ, VIEW_RADIUS_CELLS * (double) cell)) {
				int x = worldToScreenX(icon.x());
				int y = worldToScreenZ(icon.z());
				graphics.fill(x - 2, y - 2, x + 2, y + 2, icon.color());
			}

			var player = Minecraft.getInstance().player;
			if (player != null) {
				int px = worldToScreenX(player.getX());
				int py = worldToScreenZ(player.getZ());
				graphics.fill(px - 3, py - 3, px + 3, py + 3, PLAYER_COLOR);
				graphics.outline(px - 3, py - 3, 6, 6, 0xFF10333A);

				// A small offset dot in the player's real facing direction (same forward-vector
				// formula vanilla itself uses at pitch 0, `Entity#calculateViewVector`) - poor man's
				// compass arrow without needing a rotated-sprite/matrix-transform draw call.
				double yawRad = Math.toRadians(player.getYRot());
				double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
				int fdx = px + (int) Math.round(fx * 7);
				int fdz = py + (int) Math.round(fz * 7);
				graphics.fill(fdx - 1, fdz - 1, fdx + 1, fdz + 1, 0xFFFFFFFF);
			}
		}

		for (var waypoint : KubeUIWaypoints.all()) {
			if (!waypoint.dimension().equals(dimension)) {
				continue;
			}
			int x = worldToScreenX(waypoint.x());
			int y = worldToScreenZ(waypoint.z());
			graphics.fill(x - 3, y - 3, x + 3, y + 3, waypoint.color());
			graphics.outline(x - 3, y - 3, 6, 6, 0xFF000000);

			int labelWidth = font.width(waypoint.name());
			graphics.fill(x + 5, y - 5, x + 9 + labelWidth, y + 5, 0xA0000000);
			graphics.text(this.font, waypoint.name(), x + 7, y - 4, 0xFFFFFFFF, false);
		}

		drawCompass(graphics, mapLeft, mapTop, mapWidth);
		drawScaleBar(graphics, mapRight, mapBottom);

		graphics.disableScissor();
		graphics.outline(mapLeft, mapTop, mapWidth, mapHeight, PANEL_BORDER);

		drawTopBar(graphics);
		drawSidebar(graphics, dimension);
		drawInfoPanel(graphics, mapLeft, mapBottom, mouseX, mouseY);

		super.render(realGraphics, mouseX, mouseY, delta);
	}

	private void drawTopBar(GuiGraphicsExtractor graphics) {
		graphics.fill(0, 0, width, TOP_BAR_HEIGHT, PANEL_BG);
		graphics.horizontalLine(0, width - 1, TOP_BAR_HEIGHT, PANEL_BORDER);
		graphics.text(this.font, this.title, 8, 10, KubeUITheme.titleColor(), false);
	}

	private void drawSidebar(GuiGraphicsExtractor graphics, String dimension) {
		int sidebarLeft = this.width - SIDEBAR_WIDTH;
		graphics.fill(sidebarLeft, TOP_BAR_HEIGHT, this.width, this.height, PANEL_BG);
		graphics.verticalLine(sidebarLeft, TOP_BAR_HEIGHT, this.height - 1, PANEL_BORDER);
		graphics.text(this.font, "Waypoints", sidebarLeft + 8, TOP_BAR_HEIGHT + 6, KubeUITheme.titleColor(), false);

		boolean any = KubeUIWaypoints.all().stream().anyMatch(w -> w.dimension().equals(dimension));
		if (!any) {
			graphics.text(this.font, "None yet - use", sidebarLeft + 8, TOP_BAR_HEIGHT + MARGIN + 16, dimText(), false);
			graphics.text(this.font, "\"Add Waypoint\".", sidebarLeft + 8, TOP_BAR_HEIGHT + MARGIN + 26, dimText(), false);
		}
	}

	private void drawInfoPanel(GuiGraphicsExtractor graphics, int mapLeft, int mapBottom, int mouseX, int mouseY) {
		boolean hovering = insideViewport(mouseX, mouseY);
		double worldX = hovering ? screenToWorldX(mouseX) : centerX;
		double worldZ = hovering ? screenToWorldZ(mouseY) : centerZ;

		var lines = new ArrayList<String>();
		lines.add("X: " + (int) worldX + "  Z: " + (int) worldZ);
		if (viewingCurrentPosition()) {
			String biome = biomeNameAt(worldX, worldZ);
			if (biome != null) {
				lines.add("Biome: " + biome);
			}
		}
		lines.add(String.format(java.util.Locale.ROOT, "Zoom: %.1f blocks/px", blocksPerPixel));

		int panelWidth = 0;
		for (var line : lines) {
			panelWidth = Math.max(panelWidth, font.width(line));
		}
		panelWidth += 10;
		int panelHeight = lines.size() * 10 + 6;
		int panelX = mapLeft + 6;
		int panelY = mapBottom - panelHeight - 6;

		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, INFO_BG);
		graphics.outline(panelX, panelY, panelWidth, panelHeight, PANEL_BORDER);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(this.font, lines.get(i), panelX + 5, panelY + 4 + i * 10, 0xFFE0E0E0, false);
		}
	}

	private void drawCompass(GuiGraphicsExtractor graphics, int mapLeft, int mapTop, int mapWidth) {
		graphics.centeredText(this.font, "N", mapLeft + mapWidth / 2, mapTop + 3, 0xFFE0E0E0);
	}

	private void drawScaleBar(GuiGraphicsExtractor graphics, int mapRight, int mapBottom) {
		int barLength = 50;
		int x1 = mapRight - 10 - barLength;
		int x2 = mapRight - 10;
		int y = mapBottom - 12;

		graphics.horizontalLine(x1, x2, y, 0xFFE0E0E0);
		graphics.verticalLine(x1, y - 2, y + 2, 0xFFE0E0E0);
		graphics.verticalLine(x2, y - 2, y + 2, 0xFFE0E0E0);

		String label = (int) (barLength * blocksPerPixel) + " blocks";
		graphics.centeredText(this.font, label, (x1 + x2) / 2, y - 10, 0xFFE0E0E0);
	}

	private String biomeNameAt(double worldX, double worldZ) {
		var level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		var pos = BlockPos.containing(worldX, caveY, worldZ);
		return level.getBiome(pos).unwrapKey().map(key -> prettify(key.location().getPath())).orElse(null);
	}

	private static String prettify(String path) {
		var parts = path.split("_");
		var result = new StringBuilder();
		for (var part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!result.isEmpty()) {
				result.append(' ');
			}
			result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return result.toString();
	}

	private static int dimText() {
		return (KubeUITheme.textColor() & 0x00FFFFFF) | 0xA0000000;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		var event = new MouseButtonEvent(mouseX, mouseY, button);
		if (event.button() == 0 && insideViewport(event.x(), event.y())) {
			centerX -= dx * blocksPerPixel;
			centerZ -= dy * blocksPerPixel;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!insideViewport(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}
		if (scrollY > 0) {
			blocksPerPixel = Math.max(0.5, blocksPerPixel / 1.25);
		} else if (scrollY < 0) {
			blocksPerPixel = Math.min(64.0, blocksPerPixel * 1.25);
		}
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scancode, int modifiers) {
		var event = new KeyEvent(keyCode, scancode, modifiers);
		double step = 20 * blocksPerPixel;
		switch (event.key()) {
			case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> centerX -= step;
			case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> centerX += step;
			case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> centerZ -= step;
			case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> centerZ += step;
			default -> {
				return super.keyPressed(keyCode, scancode, modifiers);
			}
		}
		return true;
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}
}
