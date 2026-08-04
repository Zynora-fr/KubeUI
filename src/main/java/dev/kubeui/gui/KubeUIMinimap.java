package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Arrays;

/// A simplified top-down minimap (`.map(id, radius)`), always centered on the local player (like
/// a Journeymap-style minimap) and colored the same way vanilla maps are: real per-block
/// [MapColor], sampled at the top solid block (`Heightmap.Types.MOTION_BLOCKING`) - not a fake
/// gradient/placeholder.
///
/// Two things that made an earlier version of this widget genuinely laggy, both fixed here:
///  - It sampled and drew one real pixel per block (`radius * 2` squared world lookups *and* fill
///    calls - thousands of each for a reasonably-sized map, the fill calls repeated *every frame*
///    since rendering, unlike sampling, can't be skipped). This version samples/draws a fixed
///    `GRID x GRID` cell grid regardless of `radius` or the widget's pixel size, capping both
///    costs at a constant `GRID * GRID` instead of scaling with how far the player wants to see.
///  - It only ever sampled once, when built - fine for a static map, not for "follows the player".
///    Re-sampling on every tick the player has moved at all would just move the lag into `tick()`
///    instead of removing it, so [#tick()] only triggers a real resample (world lookups) once the
///    player has moved far enough that the visible area meaningfully changed, throttled to at most
///    once per [#RESAMPLE_MIN_INTERVAL_MS]. The player marker itself still moves every frame - it's
///    plain arithmetic against the last sample's center, no world access involved.
class KubeUIMinimap extends AbstractWidget implements KubeUINarratable {
	private static final int GRID = 32;
	private static final int RESAMPLE_MIN_INTERVAL_MS = 500;
	private static final int BORDER_COLOR = 0xFFEAF3F3;
	private static final int OUTER_RING_COLOR = 0xFF6B7679;

	private final int radius;
	private final Font font;
	private final int[] cells = new int[GRID * GRID];
	private int sampledCenterX;
	private int sampledCenterZ;
	private long lastSampleAtMs;
	private String narration;

	KubeUIMinimap(int x, int y, int width, int height, int radius, Font font) {
		super(x, y, width, height, Component.literal("Map"));
		this.radius = Math.max(GRID, radius);
		this.font = font;
		resample(playerBlockX(), playerBlockZ());
	}

	private static int playerBlockX() {
		var player = Minecraft.getInstance().player;
		return player != null ? player.getBlockX() : 0;
	}

	private static int playerBlockZ() {
		var player = Minecraft.getInstance().player;
		return player != null ? player.getBlockZ() : 0;
	}

	/// Called every client tick by [KubeUIScreen#tick] while this widget is on screen.
	void tick() {
		long now = System.currentTimeMillis();
		if (now - lastSampleAtMs < RESAMPLE_MIN_INTERVAL_MS) {
			return;
		}

		int x = playerBlockX();
		int z = playerBlockZ();
		int moveThreshold = Math.max(4, radius / 3);

		if (Math.abs(x - sampledCenterX) >= moveThreshold || Math.abs(z - sampledCenterZ) >= moveThreshold) {
			resample(x, z);
		}
	}

	private void resample(int centerX, int centerZ) {
		lastSampleAtMs = System.currentTimeMillis();
		sampledCenterX = centerX;
		sampledCenterZ = centerZ;

		var level = Minecraft.getInstance().level;
		if (level == null) {
			Arrays.fill(cells, 0xFF404040);
			return;
		}

		double blocksPerCell = radius * 2.0 / GRID;
		var pos = new BlockPos.MutableBlockPos();

		for (int gz = 0; gz < GRID; gz++) {
			int worldZ = centerZ - radius + (int) (gz * blocksPerCell);
			for (int gx = 0; gx < GRID; gx++) {
				int worldX = centerX - radius + (int) (gx * blocksPerCell);
				int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ);
				pos.set(worldX, topY - 1, worldZ);
				MapColor color = level.getBlockState(pos).getMapColor(level, pos);
				cells[gz * GRID + gx] = color == MapColor.NONE ? 0xFF1E1E1E : color.calculateARGBColor(MapColor.Brightness.NORMAL);
			}
		}
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		float cellWidth = getWidth() / (float) GRID;
		float cellHeight = getHeight() / (float) GRID;
		double gridCenter = GRID / 2.0;
		double maxDist = GRID / 2.0;

		for (int gz = 0; gz < GRID; gz++) {
			double dz = gz + 0.5 - gridCenter;
			int y0 = getY() + (int) (gz * cellHeight);
			int y1 = getY() + (int) ((gz + 1) * cellHeight);

			for (int gx = 0; gx < GRID; gx++) {
				double dx = gx + 0.5 - gridCenter;
				double dist = Math.sqrt(dx * dx + dz * dz);
				// Round the visible area off into a disc (like Journeymap's default minimap)
				// instead of a plain square, and pick out the outermost ring of cells as a border.
				if (dist > maxDist) {
					continue;
				}

				int x0 = getX() + (int) (gx * cellWidth);
				int x1 = getX() + (int) ((gx + 1) * cellWidth);
				int color = dist > maxDist - 1.2 ? BORDER_COLOR : cells[gz * GRID + gx];
				graphics.fill(x0, y0, x1, y1, color);
			}
		}

		drawPlayerMarker(graphics);
		drawCardinalMarker(graphics);
	}

	private void drawCardinalMarker(GuiGraphicsExtractor graphics) {
		graphics.centeredText(font, "N", getX() + getWidth() / 2, getY() + 2, OUTER_RING_COLOR);
	}

	/// Positioned every frame from the player's *current* position relative to the last sample's
	/// center - cheap (no world access), so the marker moves smoothly even between resamples.
	private void drawPlayerMarker(GuiGraphicsExtractor graphics) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		double pixelsPerBlock = getWidth() / (double) (radius * 2);
		int markerX = getX() + getWidth() / 2 + (int) Math.round((player.getX() - sampledCenterX) * pixelsPerBlock);
		int markerY = getY() + getHeight() / 2 + (int) Math.round((player.getZ() - sampledCenterZ) * pixelsPerBlock);
		markerX = Math.clamp(markerX, getX(), getX() + getWidth());
		markerY = Math.clamp(markerY, getY(), getY() + getHeight());

		graphics.fill(markerX - 2, markerY - 2, markerX + 2, markerY + 2, 0xFFFF5555);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : "Map, radius " + radius + ", centered on you"));
	}
}
