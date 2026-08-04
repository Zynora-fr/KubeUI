package dev.kubeui.gui;

import org.lwjgl.glfw.GLFW;

/// Resolves the small set of key names `.hotkey(...)` accepts into a raw GLFW key code (the same
/// numeric space `KeyEvent#key()` already uses elsewhere in this codebase) - single letters/digits
/// (`"R"`, `"5"`), function keys (`"F1"`-`"F12"`), and a handful of named keys, rather than
/// requiring a script to know GLFW constants or vanilla's `"key.keyboard.*"` translation strings.
final class KubeUIKeyNames {
	private KubeUIKeyNames() {
	}

	static Integer resolve(String name) {
		if (name == null || name.isEmpty()) {
			return null;
		}

		String upper = name.trim().toUpperCase(java.util.Locale.ROOT);

		if (upper.length() == 1) {
			char c = upper.charAt(0);
			if (c >= 'A' && c <= 'Z') {
				return GLFW.GLFW_KEY_A + (c - 'A');
			}
			if (c >= '0' && c <= '9') {
				return GLFW.GLFW_KEY_0 + (c - '0');
			}
		}

		if (upper.matches("F([1-9]|1[0-9]|2[0-5])")) {
			int n = Integer.parseInt(upper.substring(1));
			return GLFW.GLFW_KEY_F1 + (n - 1);
		}

		return switch (upper) {
			case "ESCAPE", "ESC" -> GLFW.GLFW_KEY_ESCAPE;
			case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
			case "SPACE" -> GLFW.GLFW_KEY_SPACE;
			case "TAB" -> GLFW.GLFW_KEY_TAB;
			case "DELETE", "DEL" -> GLFW.GLFW_KEY_DELETE;
			case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
			case "UP" -> GLFW.GLFW_KEY_UP;
			case "DOWN" -> GLFW.GLFW_KEY_DOWN;
			case "LEFT" -> GLFW.GLFW_KEY_LEFT;
			case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
			default -> null;
		};
	}
}
