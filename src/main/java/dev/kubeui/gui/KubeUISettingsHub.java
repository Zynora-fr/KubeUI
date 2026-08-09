package dev.kubeui.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.kubeui.KubeUI;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// A unified settings hub other mods (or this same one) register a section into
/// (`KubeUISettingsHub.register(modId, name, section => {...})`), exposed to KubeJS as the global
/// `KubeUISettingsHub` binding (client scripts only) - same real "public standalone class, no
/// KubeUIScreenBuilder wrapping needed" shape [KubeUISidebar] already established for its own icon
/// registry. A third-party mod only ever calls this one optional extension point - no hard
/// dependency on KubeUI needed to add a section, the same real "call if present" integration shape
/// [KubeUIPermissions] already uses.
///
/// Bundles several roadmap entries onto one real registry rather than separate classes, the same
/// "one system, several angles" reasoning [KubeUIGuilds]/[KubeUIMachineBridge] already used for
/// theirs: sections + search (a hub screen built from [#sections]/[#matching] is a script's own
/// job, same as every other KubeUI screen), named settings profiles with a real global-vs-scoped
/// value split, conflict detection between two sections declaring different defaults for the same
/// key, and a one-time-per-id onboarding flag.
public final class KubeUISettingsHub {
	public record Section(String id, String modId, String name, Consumer<KubeUIScreenBuilder> onOpen, Map<String, Object> declaredDefaults) {
	}

	private static final Map<String, Section> SECTIONS = new LinkedHashMap<>();

	/// `profile -> (scope -> (key -> value))` - `scope` is any caller-chosen tag (`"global"`, or a
	/// script's own idea of "this server", e.g. the address from `Minecraft#getCurrentServer()`) -
	/// deliberately not inferred here, so this stays agnostic about how "which server" is decided.
	private static final Map<String, Map<String, Map<String, Object>>> PROFILES = new LinkedHashMap<>();
	private static String activeProfile = "default";

	private static final java.util.Set<String> ONBOARDING_SEEN = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private static boolean onboardingLoaded = false;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type PROFILE_TYPE = new TypeToken<Map<String, Map<String, Object>>>() {
	}.getType();

	private KubeUISettingsHub() {
	}

	/// Registers (or replaces) a section - `declaredDefaults` (may be empty) is `{settingKey:
	/// defaultValue}` this section "owns", checked against every other section's own declared
	/// defaults for [#conflicts]. `onOpen` is called with a fresh builder to append its own widgets
	/// to - the hub screen itself (iterating [#sections], calling each `onOpen`) is a script's own
	/// job, same generic "KubeUI has no opinion on what the screen looks like" shape
	/// `KubeUIRemoteScreens` already uses.
	public static void register(String modId, String name, Consumer<KubeUIScreenBuilder> onOpen, Map<String, Object> declaredDefaults) {
		if (modId == null || name == null || onOpen == null) {
			KubeUI.LOGGER.error("KubeUISettingsHub.register needs a non-null modId, name and onOpen - ignoring (modId={})", modId);
			return;
		}
		String id = modId + ":" + name;
		SECTIONS.put(id, new Section(id, modId, name, safe(id, onOpen), declaredDefaults == null ? Map.of() : Map.copyOf(declaredDefaults)));
	}

	public static void unregister(String modId, String name) {
		SECTIONS.remove(modId + ":" + name);
	}

	public static List<Section> sections() {
		return List.copyOf(SECTIONS.values());
	}

	/// Case-insensitive substring match against a section's `modId`/`name` - backs a
	/// `.searchBox()`-driven filter in the hub screen a script builds.
	public static List<Section> matching(String query) {
		if (query == null || query.isBlank()) {
			return sections();
		}
		String needle = query.toLowerCase(java.util.Locale.ROOT);
		return SECTIONS.values().stream()
			.filter(s -> s.modId().toLowerCase(java.util.Locale.ROOT).contains(needle) || s.name().toLowerCase(java.util.Locale.ROOT).contains(needle))
			.toList();
	}

	/// Every pair of registered sections that declared a different default value for the same
	/// settings key - each result is `"modA:sectionA vs modB:sectionB (key)"`.
	public static List<String> conflicts() {
		var result = new ArrayList<String>();
		var all = List.copyOf(SECTIONS.values());
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				var a = all.get(i);
				var b = all.get(j);
				for (var key : a.declaredDefaults().keySet()) {
					if (b.declaredDefaults().containsKey(key) && !java.util.Objects.equals(a.declaredDefaults().get(key), b.declaredDefaults().get(key))) {
						result.add(a.id() + " vs " + b.id() + " (" + key + ")");
					}
				}
			}
		}
		return result;
	}

	// ---------------------------------------------------------------- profiles

	public static void setActiveProfile(String profile) {
		activeProfile = profile == null || profile.isBlank() ? "default" : profile;
	}

	public static String activeProfile() {
		return activeProfile;
	}

	public static List<String> profileNames() {
		return List.copyOf(PROFILES.keySet());
	}

	public static void setValue(String profile, String scope, String key, Object value) {
		PROFILES.computeIfAbsent(profile, ignored -> new LinkedHashMap<>())
			.computeIfAbsent(scope, ignored -> new LinkedHashMap<>())
			.put(key, value);
	}

	/// Reads `key` from `profile`, checking `scope` first, then `"global"` - `null` if set nowhere.
	public static Object getValue(String profile, String scope, String key) {
		var byScope = PROFILES.get(profile);
		if (byScope == null) {
			return null;
		}
		var scoped = byScope.get(scope);
		if (scoped != null && scoped.containsKey(key)) {
			return scoped.get(key);
		}
		var global = byScope.get("global");
		return global != null ? global.get(key) : null;
	}

	/// Real JSON export (same real GSON mechanism [KubeUIPreferences] already established for its
	/// own scale/theme snapshot) - portable between players/installs, one file per profile name.
	public static void exportProfile(String profile) throws IOException {
		var data = PROFILES.getOrDefault(profile, Map.of());
		var file = profileFile(profile);
		Files.createDirectories(file.getParent());
		try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(data, PROFILE_TYPE, writer);
		}
	}

	public static boolean importProfile(String profile) throws IOException {
		var file = profileFile(profile);
		if (!Files.isRegularFile(file)) {
			return false;
		}
		Map<String, Map<String, Object>> data;
		try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			data = GSON.fromJson(reader, PROFILE_TYPE);
		}
		if (data == null) {
			return false;
		}
		PROFILES.put(profile, new LinkedHashMap<>(data));
		return true;
	}

	private static Path profileFile(String profile) {
		return FMLPaths.CONFIGDIR.get().resolve("kubeui-settings-" + profile.replaceAll("[^a-zA-Z0-9_-]", "_") + ".json");
	}

	// ---------------------------------------------------------------- onboarding

	private static Path onboardingFile() {
		return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("kubeui").resolve("onboarding_seen.dat");
	}

	private static void ensureOnboardingLoaded() {
		if (onboardingLoaded) {
			return;
		}
		onboardingLoaded = true;
		var path = onboardingFile();
		if (!Files.exists(path)) {
			return;
		}
		try {
			var root = net.minecraft.nbt.NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			for (var tag : KubeUINbtCompat.getListOrEmpty(root, "seen")) {
				if (tag instanceof net.minecraft.nbt.StringTag s) {
					ONBOARDING_SEEN.add(s.getAsString());
				}
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load onboarding state from disk", ex);
		}
	}

	private static void saveOnboarding() {
		var root = new net.minecraft.nbt.CompoundTag();
		var listTag = new net.minecraft.nbt.ListTag();
		for (var id : ONBOARDING_SEEN) {
			listTag.add(net.minecraft.nbt.StringTag.valueOf(id));
		}
		root.put("seen", listTag);
		try {
			var path = onboardingFile();
			Files.createDirectories(path.getParent());
			net.minecraft.nbt.NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save onboarding state to disk", ex);
		}
	}

	/// Shows `onOpen`'s screen the first time this is ever called for `onboardingId` on this
	/// install (persisted locally), a no-op every time after - the real "first-connect wizard" ask,
	/// scoped to "first time this client has seen this modpack's onboarding id" rather than a true
	/// per-modpack identity this session has no generic way to detect.
	///
	/// Real, reported crash this guards against: `client_scripts` top-level code runs during
	/// KubeJS's own construction (`KubeJS.<init>` → `ScriptManager.reload`), *before*
	/// `Minecraft.getInstance()` exists - a script calling this at top level (instead of from
	/// `ClientEvents.loggedIn(...)`, the real "client is actually ready" event
	/// `kubeui_test_menu.js`'s own auto-open already uses) would otherwise take down mod
	/// construction entirely with a `NullPointerException` reading `.gameDirectory` off a `null`
	/// `Minecraft` instance. A clear log message and a no-op is a far better failure mode than
	/// crashing every mod in the pack over one script's timing mistake.
	public static void showOnboardingOnce(String onboardingId, Runnable onOpen) {
		if (net.minecraft.client.Minecraft.getInstance() == null) {
			KubeUI.LOGGER.error(
				"KubeUISettingsHub.showOnboardingOnce('{}') was called before the client is ready - "
					+ "call it from ClientEvents.loggedIn(...), not at the top level of a client_scripts file. Ignoring.",
				onboardingId
			);
			return;
		}
		ensureOnboardingLoaded();
		if (ONBOARDING_SEEN.contains(onboardingId)) {
			return;
		}
		ONBOARDING_SEEN.add(onboardingId);
		saveOnboarding();
		try {
			onOpen.run();
		} catch (Exception ex) {
			KubeUI.LOGGER.error("KubeUISettingsHub onboarding callback for '{}' threw an exception", onboardingId, ex);
		}
	}

	private static <T> Consumer<T> safe(String id, Consumer<T> callback) {
		return arg -> {
			try {
				callback.accept(arg);
			} catch (Exception ex) {
				KubeUI.LOGGER.error("KubeUISettingsHub callback for '{}' threw an exception", id, ex);
			}
		};
	}
}
