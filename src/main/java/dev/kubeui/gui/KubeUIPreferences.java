package dev.kubeui.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/// `/kubeui export`/`/kubeui import` - a portable snapshot of everything [KubeUIConfig]'s
/// `COMMON_SPEC` and [KubeUIWindowPositions] hold, for transferring one player's KubeUI
/// preferences between installs/machines. Deliberately *not* every setting KubeUI has - only the
/// ones that are meaningfully "this player's preference" rather than per-world/server state (see
/// [KubeUIConfig#SERVER_SPEC], not included here).
public final class KubeUIPreferences {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type EXPORT_TYPE = new TypeToken<Map<String, Object>>() {
	}.getType();

	private static final Path EXPORT_FILE = FMLPaths.CONFIGDIR.get().resolve("kubeui-export.json");

	private KubeUIPreferences() {
	}

	public static Path exportFile() {
		return EXPORT_FILE;
	}

	public static void export() throws IOException {
		var data = new LinkedHashMap<String, Object>();
		data.put("defaultScale", KubeUIConfig.defaultScale.get());
		data.put("defaultFontScale", KubeUIConfig.defaultFontScale.get());
		data.put("defaultTheme", KubeUIConfig.defaultTheme.get());
		data.put("persistScaleAndTheme", KubeUIConfig.persistScaleAndTheme.get());
		data.put("windowPositions", KubeUIWindowPositions.snapshot());

		Files.createDirectories(EXPORT_FILE.getParent());
		try (var writer = Files.newBufferedWriter(EXPORT_FILE, StandardCharsets.UTF_8)) {
			GSON.toJson(data, EXPORT_TYPE, writer);
		}
	}

	/// Returns false (without throwing) if [#exportFile] doesn't exist yet - a script/player needs
	/// to `/kubeui export` at least once, on some install, before there's anything to import.
	public static boolean importPreferences() throws IOException {
		if (!Files.isRegularFile(EXPORT_FILE)) {
			return false;
		}

		Map<String, Object> data;
		try (var reader = Files.newBufferedReader(EXPORT_FILE, StandardCharsets.UTF_8)) {
			data = GSON.fromJson(reader, EXPORT_TYPE);
		}
		if (data == null) {
			return false;
		}

		if (data.get("defaultScale") instanceof Number n) {
			KubeUIConfig.defaultScale.set(n.doubleValue());
			KubeUITheme.setScale(n.floatValue());
		}
		if (data.get("defaultFontScale") instanceof Number n) {
			KubeUIConfig.defaultFontScale.set(n.doubleValue());
			KubeUITheme.setFontScale(n.floatValue());
		}
		if (data.get("defaultTheme") instanceof String name) {
			KubeUIConfig.defaultTheme.set(name);
			KubeUITheme.applyPreset(name);
		}
		if (data.get("persistScaleAndTheme") instanceof Boolean b) {
			KubeUIConfig.persistScaleAndTheme.set(b);
		}
		KubeUIConfig.COMMON_SPEC.save();

		if (data.get("windowPositions") instanceof Map<?, ?> rawPositions) {
			var positions = new java.util.HashMap<String, int[]>();
			for (var entry : rawPositions.entrySet()) {
				if (entry.getKey() instanceof String key && entry.getValue() instanceof java.util.List<?> xy && xy.size() == 2
					&& xy.get(0) instanceof Number x && xy.get(1) instanceof Number y) {
					positions.put(key, new int[] {x.intValue(), y.intValue()});
				}
			}
			KubeUIWindowPositions.restore(positions);
		}

		return true;
	}
}
