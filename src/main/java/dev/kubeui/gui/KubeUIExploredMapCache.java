package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/// A progressive explored-map cache - a real, persisted color per `CELL_SIZE`-block
/// cell, growing as the player actually visits new areas rather than a fake fog-of-war overlay.
/// Consistent with the minimap's own sampling being limited to what's actually loaded:
/// a cell only ever gets an entry once something *actually samples it* ([#markExplored], driven by
/// [KubeUIMapClientEvents] while a world is loaded, or [KubeUIWorldMapScreen] itself while open) -
/// never pre-filled. An unvisited cell simply has no entry and renders blank/grey.
///
/// Only the `"surface"` layer (the default) is sampled automatically in the background as
/// the player walks around - the `"caves"` layer only gets fresh entries while the world map
/// screen itself is open and that layer is the one being viewed (knowing "is the player currently
/// underground" well enough to justify an extra always-on background sampler wasn't worth the
/// added complexity for a real but secondary layer).
public final class KubeUIExploredMapCache {
	static final int CELL_SIZE = 16;

	private static final Map<String, Integer> CELLS = new HashMap<>();
	private static boolean loaded = false;
	private static volatile boolean dirty = false;

	private KubeUIExploredMapCache() {
	}

	private static String key(String dimension, String layer, int cellX, int cellZ) {
		return dimension + '|' + layer + '|' + cellX + '|' + cellZ;
	}

	private static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("kubeui").resolve("explored_map.dat");
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;

		var path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			var root = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			for (var entry : root.getListOrEmpty("cells")) {
				if (entry instanceof CompoundTag tag) {
					CELLS.put(tag.getStringOr("key", ""), tag.getIntOr("color", 0));
				}
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load explored map cache from disk", ex);
		}
	}

	public static void save() {
		if (!dirty) {
			return;
		}
		dirty = false;

		var root = new CompoundTag();
		var listTag = new ListTag();
		CELLS.forEach((cellKey, color) -> {
			var tag = new CompoundTag();
			tag.putString("key", cellKey);
			tag.putInt("color", color);
			listTag.add(tag);
		});
		root.put("cells", listTag);

		try {
			var path = file();
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save explored map cache to disk", ex);
		}
	}

	/// Samples the real top block (`"surface"`) or a fixed Y (any other layer id, e.g.
	/// `"caves"`) at the cell containing `worldX`/`worldZ` and stores it - a no-op if that block
	/// column isn't actually loaded (never marks a cell "explored" from a guess).
	public static void markExplored(Level level, String dimension, String layer, int worldX, int worldZ, int caveY) {
		ensureLoaded();
		int cellX = Math.floorDiv(worldX, CELL_SIZE);
		int cellZ = Math.floorDiv(worldZ, CELL_SIZE);
		int sampleX = cellX * CELL_SIZE + CELL_SIZE / 2;
		int sampleZ = cellZ * CELL_SIZE + CELL_SIZE / 2;

		if (!level.hasChunkAt(sampleX, sampleZ)) {
			return;
		}

		int color = sampleColor(level, layer, sampleX, sampleZ, caveY);
		String cacheKey = key(dimension, layer, cellX, cellZ);
		if (!CELLS.containsKey(cacheKey)) {
			CELLS.put(cacheKey, color);
			dirty = true;
		}
	}

	private static int sampleColor(Level level, String layer, int x, int z, int caveY) {
		var pos = new BlockPos.MutableBlockPos();
		if ("surface".equals(layer)) {
			int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			pos.set(x, topY - 1, z);
		} else {
			pos.set(x, caveY, z);
		}
		var color = level.getBlockState(pos).getMapColor(level, pos);
		return color == MapColor.NONE ? 0xFF1E1E1E : color.calculateARGBColor(MapColor.Brightness.NORMAL);
	}

	/// Returns `null` for a cell that's never been explored - the caller decides how to render
	/// "unknown" (blank, grey, whatever fits).
	static Integer colorOf(String dimension, String layer, int cellX, int cellZ) {
		ensureLoaded();
		return CELLS.get(key(dimension, layer, cellX, cellZ));
	}
}
