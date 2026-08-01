package dev.kubeui.gui;

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

	static int titleColor = DEFAULT_TITLE_COLOR;
	static int accentColor = DEFAULT_ACCENT_COLOR;
	static int textColor = DEFAULT_TEXT_COLOR;

	/// Multiplies every screen's pixel-based widget widths/heights (not `.width("50%")`-style
	/// percentages, which are already relative to the screen and don't need it). A personal knob
	/// for "this script's UI is too big/small on my monitor" - independent of Minecraft's own GUI
	/// Scale option, and of any single script's own choices. See `/kubeui scale`.
	static float uiScale = DEFAULT_UI_SCALE;

	private KubeUITheme() {
	}

	static void set(int titleColor, int accentColor, int textColor) {
		KubeUITheme.titleColor = titleColor;
		KubeUITheme.accentColor = accentColor;
		KubeUITheme.textColor = textColor;
	}

	static void reset() {
		titleColor = DEFAULT_TITLE_COLOR;
		accentColor = DEFAULT_ACCENT_COLOR;
		textColor = DEFAULT_TEXT_COLOR;
	}

	static void setScale(float scale) {
		uiScale = Math.max(MIN_UI_SCALE, Math.min(MAX_UI_SCALE, scale));
	}

	static void resetScale() {
		uiScale = DEFAULT_UI_SCALE;
	}
}
