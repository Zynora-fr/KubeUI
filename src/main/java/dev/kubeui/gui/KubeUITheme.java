package dev.kubeui.gui;

import dev.kubeui.KubeUI;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/// Global color theme, applied to newly-built screens. Only affects what KubeUI itself draws
/// directly (screen title, progress bar fill, divider) - vanilla widgets (button, checkbox, text
/// field, ...) are drawn from Minecraft's own sprites/theme and aren't recolored by this.
final class KubeUITheme {
	static final int DEFAULT_TITLE_COLOR = 0xFFFFFFFF;
	static final int DEFAULT_ACCENT_COLOR = 0xFF3B8527;
	static final int DEFAULT_TEXT_COLOR = 0xFFFFFFFF;
	static final float DEFAULT_UI_SCALE = 1.0f;
	static final float MIN_UI_SCALE = 0.5f;
	static final float MAX_UI_SCALE = 2.0f;
	static final float DEFAULT_FONT_SCALE = 1.0f;
	static final float MIN_FONT_SCALE = 0.5f;
	static final float MAX_FONT_SCALE = 2.0f;

	/// How long a color change mid-session takes to fade to its new values instead of snapping
	/// instantly. Only matters for a change made while a screen is already open and rendering -
	/// there's nothing to fade *from* the very first time a color is ever read.
	private static final long TRANSITION_MS = 300;

	/// Built-in named presets (title/accent/text, same ARGB packing as [#set]) - see [#applyPreset].
	private static final Map<String, int[]> BUILTIN_PRESETS = Map.of(
		"default", new int[] {DEFAULT_TITLE_COLOR, DEFAULT_ACCENT_COLOR, DEFAULT_TEXT_COLOR},
		"dark", new int[] {0xFFFFFFFF, 0xFF3B8527, 0xFFE0E0E0},
		"light", new int[] {0xFF1A1A1A, 0xFF2266CC, 0xFF2A2A2A},
		"high-contrast", new int[] {0xFFFFFFFF, 0xFFFFFF00, 0xFFFFFFFF}
	);

	/// Script-registered presets (see [#registerPreset]) - checked before [#BUILTIN_PRESETS], so a
	/// script can shadow a built-in name if it really wants to. Shared across every script/addon in
	/// the session (same reasoning as [KubeUISafeArea]'s registry): a preset registered once, by
	/// name, is exactly what makes it "shareable" between otherwise-unrelated scripts.
	private static final Map<String, int[]> customPresets = new HashMap<>();

	private static int fromTitle = DEFAULT_TITLE_COLOR, fromAccent = DEFAULT_ACCENT_COLOR, fromText = DEFAULT_TEXT_COLOR;
	private static int toTitle = DEFAULT_TITLE_COLOR, toAccent = DEFAULT_ACCENT_COLOR, toText = DEFAULT_TEXT_COLOR;
	private static long transitionStart = 0;

	// `/kubeui theme preview <name>` state - checked lazily from the color getters (same
	// check-on-read pattern KubeUIToast uses for expiry, no dedicated tick hook needed).
	// `previewRevertTo` is whatever the theme *was* right before the preview started, captured
	// once so previewing twice in a row still reverts to the real original, not the first preview.
	private static long previewExpiresAt = 0;
	private static int[] previewRevertTo = null;

	/// Multiplies every screen's pixel-based widget widths/heights (not `.width("50%")`-style
	/// percentages, which are already relative to the screen and don't need it). A personal knob
	/// for "this script's UI is too big/small on my monitor" - independent of Minecraft's own GUI
	/// Scale option, and of any single script's own choices. See `/kubeui scale`.
	static float uiScale = DEFAULT_UI_SCALE;

	/// Independent of [#uiScale]: scales *text only* (drawn via a real transform around each draw
	/// call, same mechanism as [KubeUIScreenBuilder#renderScale]) without touching box/widget sizes -
	/// for enlarging text on a screen whose layout already fits. See `/kubeui fontscale`.
	static float fontScale = DEFAULT_FONT_SCALE;

	private KubeUITheme() {
	}

	static void set(int titleColor, int accentColor, int textColor) {
		startTransition(titleColor, accentColor, textColor);
	}

	static void reset() {
		startTransition(DEFAULT_TITLE_COLOR, DEFAULT_ACCENT_COLOR, DEFAULT_TEXT_COLOR);
	}

	/// Applies a built-in (`"default"`, `"dark"`, `"light"`, `"high-contrast"`) or script-registered
	/// (see [#registerPreset]) named preset. Logs an error and leaves the current theme unchanged if
	/// `name` isn't recognized - same "never silently do nothing without saying why" convention as
	/// [KubeUIKeyNames#resolve]. Returns whether `name` was actually recognized/applied - callers
	/// that might persist `name` as a new default (see `KubeUIScreenBuilder#setTheme(String, boolean)`)
	/// use this to avoid writing an unrecognized name to disk.
	static boolean applyPreset(String name) {
		var key = name.toLowerCase(Locale.ROOT);
		var colors = customPresets.containsKey(key) ? customPresets.get(key) : BUILTIN_PRESETS.get(key);
		if (colors == null) {
			KubeUI.LOGGER.error("KubeUI: unknown theme preset '{}' - expected one of {} or a name registered via KubeUI.registerThemePreset(...)", name, BUILTIN_PRESETS.keySet());
			return false;
		}
		startTransition(colors[0], colors[1], colors[2]);
		return true;
	}

	static void registerPreset(String name, int titleColor, int accentColor, int textColor) {
		customPresets.put(name.toLowerCase(Locale.ROOT), new int[] {titleColor, accentColor, textColor});
	}

	/// Applies a named preset (see [#applyPreset]) temporarily - reverts back to whatever the theme
	/// was right before, `durationMs` later. Reverting is a normal (fading) transition too, not a
	/// snap back. Previewing again while already previewing extends/restarts the timer but still
	/// reverts to the real pre-preview theme, not whatever was mid-preview.
	static boolean previewPreset(String name, int durationMs) {
		var key = name.toLowerCase(Locale.ROOT);
		var colors = customPresets.containsKey(key) ? customPresets.get(key) : BUILTIN_PRESETS.get(key);
		if (colors == null) {
			KubeUI.LOGGER.error("KubeUI: unknown theme preset '{}' - expected one of {} or a name registered via KubeUI.registerThemePreset(...)", name, BUILTIN_PRESETS.keySet());
			return false;
		}

		if (previewRevertTo == null) {
			previewRevertTo = new int[] {titleColor(), accentColor(), textColor()};
		}
		previewExpiresAt = System.currentTimeMillis() + Math.max(500, durationMs);
		startTransition(colors[0], colors[1], colors[2]);
		return true;
	}

	/// Reverts an expired `/kubeui theme preview` early, if one is running. No-op otherwise.
	private static void checkPreviewExpiry() {
		if (previewRevertTo != null && System.currentTimeMillis() >= previewExpiresAt) {
			int[] revertTo = previewRevertTo;
			previewRevertTo = null;
			previewExpiresAt = 0;
			startTransition(revertTo[0], revertTo[1], revertTo[2]);
		}
	}

	private static void startTransition(int titleColor, int accentColor, int textColor) {
		fromTitle = titleColor();
		fromAccent = accentColor();
		fromText = textColor();
		toTitle = titleColor;
		toAccent = accentColor;
		toText = textColor;
		transitionStart = System.currentTimeMillis();
	}

	static int titleColor() {
		checkPreviewExpiry();
		return lerpArgb(fromTitle, toTitle, transitionT());
	}

	static int accentColor() {
		checkPreviewExpiry();
		return lerpArgb(fromAccent, toAccent, transitionT());
	}

	static int textColor() {
		checkPreviewExpiry();
		return lerpArgb(fromText, toText, transitionT());
	}

	private static float transitionT() {
		float t = (System.currentTimeMillis() - transitionStart) / (float) TRANSITION_MS;
		return Math.max(0f, Math.min(1f, t));
	}

	private static int lerpArgb(int from, int to, float t) {
		if (t >= 1f) {
			return to;
		}
		int a = lerpChannel(from >>> 24, to >>> 24, t);
		int r = lerpChannel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
		int g = lerpChannel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
		int b = lerpChannel(from & 0xFF, to & 0xFF, t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	private static int lerpChannel(int from, int to, float t) {
		return Math.round(from + (to - from) * t);
	}

	static void setScale(float scale) {
		uiScale = Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, scale));
	}

	static void resetScale() {
		uiScale = DEFAULT_UI_SCALE;
	}

	static void setFontScale(float scale) {
		fontScale = Math.max(MIN_FONT_SCALE, Math.min(MAX_FONT_SCALE, scale));
	}

	static void resetFontScale() {
		fontScale = DEFAULT_FONT_SCALE;
	}
}
