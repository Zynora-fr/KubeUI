package dev.kubeui.gui;

import net.minecraft.client.gui.screens.Screen;

/// Compatibility shim mirroring 26.1.2's real `net.minecraft.client.input.MouseButtonEvent`
/// record - 1.21.1's `AbstractWidget`/`Screen` hand mouse position/button as plain primitives
/// instead, so every widget's click/drag/release body (which reads `event.x()`/`event.y()`/
/// `event.button()`) can stay unchanged if the real primitives are wrapped into one of these at
/// the bridge point. `hasShiftDown()`/`hasControlDown()` read GLFW's *current* key state via
/// `Screen`'s real static helpers rather than a stored modifiers bitmask - equivalent in
/// practice since these are always read right at click time anyway.
record MouseButtonEvent(double x, double y, int button) {
	boolean hasShiftDown() {
		return Screen.hasShiftDown();
	}

	boolean hasControlDown() {
		return Screen.hasControlDown();
	}

	boolean hasAltDown() {
		return Screen.hasAltDown();
	}
}
