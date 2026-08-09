package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/// Real disk-backed configuration via NeoForge's own `ModConfigSpec` - `config/kubeui-common.toml`
/// (global, both sides, [#COMMON_SPEC]) and `serverconfig/kubeui-server.toml` (per world/server
/// save once one is joined, synced to clients, [#SERVER_SPEC]). No custom file I/O and no custom
/// hot-reload code: NeoForge's own `ConfigWatcher` already watches these files on disk and
/// re-fires [ModConfigEvent.Reloading] on an external edit (verified against the real decompiled
/// class) - [#onReload] only reacts to that, it doesn't implement watching itself. Out-of-range
/// values (e.g. a hand-edited `defaultScale` outside `[0.5, 2.0]`) are also handled for free by
/// `ModConfigSpec`'s own `defineInRange` validation/correction, not by any code here.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
public final class KubeUIConfig {
	/// Bumped only if this class's own TOML shape changes in a way that would need real migration
	/// logic - there's nothing to migrate *from* yet (this is the first tracked version), so
	/// [#applyIfCommon] only warns today; a future incompatible change adds real migration here,
	/// not a silent reset.
	private static final int CURRENT_VERSION = 1;

	public static final ModConfigSpec COMMON_SPEC;
	public static final ModConfigSpec.ConfigValue<Integer> configVersion;
	public static final ModConfigSpec.DoubleValue defaultScale;
	public static final ModConfigSpec.DoubleValue defaultFontScale;
	public static final ModConfigSpec.ConfigValue<String> defaultTheme;
	public static final ModConfigSpec.BooleanValue persistScaleAndTheme;
	public static final ModConfigSpec.IntValue maxPersistedPersistKeys;

	public static final ModConfigSpec SERVER_SPEC;
	public static final ModConfigSpec.BooleanValue perWorldWindowPositions;

	static {
		var common = new ModConfigSpec.Builder();
		common.push("general");

		configVersion = common
			.comment("Internal - do not edit by hand. Used to detect an old config shape that needs migrating.")
			.define("configVersion", CURRENT_VERSION);
		defaultScale = common
			.comment("Default KubeUI.setScale(...) factor (0.5-2.0), applied at startup and whenever a player uses /kubeui scale.")
			.defineInRange("defaultScale", 1.0, 0.5, 2.0);
		defaultFontScale = common
			.comment("Default KubeUI.setFontScale(...) factor (0.5-2.0) - see defaultScale.")
			.defineInRange("defaultFontScale", 1.0, 0.5, 2.0);
		defaultTheme = common
			.comment("Default theme preset name (\"default\", \"dark\", \"light\", \"high-contrast\", or a custom name a script registered via KubeUI.registerThemePreset(...)).")
			.define("defaultTheme", "default");
		persistScaleAndTheme = common
			.comment("If false, KubeUI.setScale(...)/.setFontScale(...)/.setTheme(...) never write to this file (session-only, the legacy default behavior) unless a script explicitly asks to persist via the boolean overload on a specific call.")
			.define("persistScaleAndTheme", true);
		maxPersistedPersistKeys = common
			.comment("Maximum number of distinct .draggable()/.builder(title, persistKey) persistKey values KubeUI remembers widget state for at once. The least-recently-used one is forgotten past this, so a script that generates many dynamic persistKeys can't grow memory use forever.")
			.defineInRange("maxPersistedPersistKeys", 500, 10, 100000);

		common.pop();
		COMMON_SPEC = common.build();

		var server = new ModConfigSpec.Builder();
		server.push("general");

		perWorldWindowPositions = server
			.comment("If true, a .draggable() window's disk-persisted position (persistKey) is remembered separately per server/world instead of shared globally across every one this player joins.")
			.define("perWorldWindowPositions", false);

		server.pop();
		SERVER_SPEC = server.build();
	}

	private KubeUIConfig() {
	}

	/// Called once from `KubeUI`'s constructor - `ModContainer#registerConfig` is only reachable
	/// there (it needs the real container NeoForge hands the mod at construction time).
	public static void register(ModContainer container) {
		container.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
		container.registerConfig(ModConfig.Type.SERVER, SERVER_SPEC);
	}

	@SubscribeEvent
	static void onLoad(ModConfigEvent.Loading event) {
		applyIfCommon(event.getConfig());
	}

	@SubscribeEvent
	static void onReload(ModConfigEvent.Reloading event) {
		applyIfCommon(event.getConfig());
	}

	/// [ModConfigEvent] fires for every registered [ModConfig] this mod has (both [#COMMON_SPEC]
	/// and [#SERVER_SPEC] share this listener) - only [#COMMON_SPEC] has values worth eagerly
	/// applying to [KubeUITheme]; [#SERVER_SPEC]'s single value is read on demand wherever it's
	/// needed instead (see the window-position persistence it configures).
	private static void applyIfCommon(ModConfig config) {
		if (config.getSpec() != COMMON_SPEC) {
			return;
		}

		if (configVersion.get() != CURRENT_VERSION) {
			KubeUI.LOGGER.warn(
				"KubeUI config was written by a different version (found {}, expected {}) - keeping every value as-is for now, a future KubeUI release may need to migrate this file.",
				configVersion.get(), CURRENT_VERSION
			);
		}

		KubeUITheme.applyPreset(defaultTheme.get());
		KubeUITheme.setScale(defaultScale.get().floatValue());
		KubeUITheme.setFontScale(defaultFontScale.get().floatValue());
	}
}
