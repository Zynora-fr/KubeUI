package dev.kubeui.gui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/// Disk persistence for a `.draggable()` window's last position, keyed by `persistKey` - real
/// cross-launch persistence (`config/kubeui-window-positions.json`), unlike
/// `KubeUIScreenBuilder.PERSISTED` (a plain in-memory `Map`, cleared on JVM restart) which
/// [KubeUIScreen] still uses first for same-session restores. A standalone JSON file rather than
/// `KubeUIConfig`'s `ModConfigSpec` - `persistKey` values are arbitrary strings a script picks at
/// runtime, not a fixed schema `ModConfigSpec` is built to describe ahead of time.
final class KubeUIWindowPositions {
	private static final Gson GSON = new Gson();
	private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, int[]>>() {
	}.getType();
	private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("kubeui-window-positions.json");

	private static Map<String, int[]> positions;

	private KubeUIWindowPositions() {
	}

	/// The last saved `(x, y)` drag offset for `persistKey`, or null if none is saved (including
	/// `persistKey == null`, for a screen with no key at all).
	static int[] get(String persistKey) {
		return persistKey == null ? null : data().get(scopedKey(persistKey));
	}

	static void set(String persistKey, int x, int y) {
		if (persistKey == null) {
			return;
		}
		data().put(scopedKey(persistKey), new int[] {x, y});
		save();
	}

	/// `KubeUIConfig.perWorldWindowPositions` (the per-world-vs-global config split, see
	/// `KubeUIConfig.SERVER_SPEC`) decides whether the same `persistKey` remembers one shared
	/// position across every world/server, or a separate one per world/server joined.
	private static String scopedKey(String persistKey) {
		if (!KubeUIConfig.perWorldWindowPositions.get()) {
			return "global|" + persistKey;
		}

		var mc = Minecraft.getInstance();
		if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
			return "sp:" + mc.getSingleplayerServer().getWorldData().getLevelName() + "|" + persistKey;
		}

		var server = mc.getCurrentServer();
		String world = server != null ? "mp:" + server.ip : "unknown";
		return world + "|" + persistKey;
	}

	/// A copy of every saved position, for `/kubeui export` - never the live map (mutating it
	/// wouldn't be saved without going through [#set]).
	static Map<String, int[]> snapshot() {
		return new HashMap<>(data());
	}

	/// Replaces every saved position with `restored` (from `/kubeui import`) and writes it out
	/// immediately, same as [#set].
	static void restore(Map<String, int[]> restored) {
		positions = new HashMap<>(restored);
		save();
	}

	private static Map<String, int[]> data() {
		if (positions == null) {
			positions = load();
		}
		return positions;
	}

	private static Map<String, int[]> load() {
		if (!Files.isRegularFile(FILE)) {
			return new HashMap<>();
		}

		try (var reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
			Map<String, int[]> loaded = GSON.fromJson(reader, MAP_TYPE);
			return loaded != null ? loaded : new HashMap<>();
		} catch (Exception ex) {
			KubeUI.LOGGER.error("Failed to read {} - starting with no saved window positions", FILE, ex);
			return new HashMap<>();
		}
	}

	private static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (var writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(positions, MAP_TYPE, writer);
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("Failed to write {}", FILE, ex);
		}
	}
}
