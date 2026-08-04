package dev.kubeui.gui;

/// Pure layout/formatting helpers with zero Minecraft dependency, so they can be unit-tested
/// directly (see `src/test/java/dev/kubeui/gui/KubeUILayoutMathTest.java`) without needing a
/// running game.
final class KubeUILayoutMath {
	private KubeUILayoutMath() {
	}

	static int resolveWidth(Integer styleWidth, int fallback) {
		return styleWidth != null ? styleWidth : fallback;
	}

	static int resolveHeight(Integer styleHeight, int fallback) {
		return styleHeight != null ? styleHeight : fallback;
	}

	static int clampInt(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	static Integer parseInt(String s) {
		if (s == null) {
			return null;
		}
		try {
			return Integer.parseInt(s.trim());
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/// Parses `"50%"` -> `0.5f`. Returns `null` for anything else (including a plain number with
	/// no `%`, so callers can tell "not a percentage" apart from "0%").
	static Float parsePercent(String s) {
		if (s == null || !s.endsWith("%")) {
			return null;
		}
		try {
			return Float.parseFloat(s.substring(0, s.length() - 1).trim()) / 100f;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	static Integer parseHexColor(String text) {
		if (text == null) {
			return null;
		}
		String cleaned = text.trim();
		if (cleaned.startsWith("#")) {
			cleaned = cleaned.substring(1);
		}
		if (cleaned.length() != 6) {
			return null;
		}
		try {
			return Integer.parseInt(cleaned, 16);
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	static String formatHexColor(int argb) {
		return String.format("#%06X", argb & 0xFFFFFF);
	}

	/// Black or white (opaque), whichever reads better on top of `backgroundArgb` - standard
	/// relative-luminance threshold, used for badge/pill text so callers don't have to pick a
	/// contrasting color themselves.
	static int readableTextColor(int backgroundArgb) {
		int r = (backgroundArgb >> 16) & 0xFF;
		int g = (backgroundArgb >> 8) & 0xFF;
		int b = backgroundArgb & 0xFF;
		double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
		return luminance > 0.6 ? 0xFF000000 : 0xFFFFFFFF;
	}
}
