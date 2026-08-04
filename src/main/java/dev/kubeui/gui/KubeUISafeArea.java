package dev.kubeui.gui;

/// Backs `KubeUI.reserveSafeArea(edge, pixels)` - a plain registry of "don't put an `.anchor(...)`
/// screen here" margins per screen edge, so a KubeUI screen can coexist with another mod's
/// persistent HUD/overlay pinned to that edge. KubeUI doesn't know what's actually drawn there (no
/// formal Minecraft/NeoForge API surveys other mods' overlays) - this only works if that other mod
/// (or a script bridging the two) calls it too. Each edge keeps the *largest* reservation asked of
/// it, since two different overlays reserving the same edge should both be respected.
final class KubeUISafeArea {
	private static int top;
	private static int bottom;
	private static int left;
	private static int right;

	private KubeUISafeArea() {
	}

	static void reserve(String edge, int pixels) {
		int amount = Math.max(0, pixels);
		switch (edge) {
			case "top" -> top = Math.max(top, amount);
			case "bottom" -> bottom = Math.max(bottom, amount);
			case "left" -> left = Math.max(left, amount);
			case "right" -> right = Math.max(right, amount);
			default -> dev.kubeui.KubeUI.LOGGER.error("KubeUI: reserveSafeArea - unknown edge '{}', expected top/bottom/left/right", edge);
		}
	}

	static void clear(String edge) {
		switch (edge) {
			case "top" -> top = 0;
			case "bottom" -> bottom = 0;
			case "left" -> left = 0;
			case "right" -> right = 0;
			default -> dev.kubeui.KubeUI.LOGGER.error("KubeUI: clearSafeArea - unknown edge '{}', expected top/bottom/left/right", edge);
		}
	}

	static int top() {
		return top;
	}

	static int bottom() {
		return bottom;
	}

	static int left() {
		return left;
	}

	static int right() {
		return right;
	}
}
