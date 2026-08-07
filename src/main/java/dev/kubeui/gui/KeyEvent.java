package dev.kubeui.gui;

/// Compatibility shim mirroring 26.1.2's real `net.minecraft.client.input.KeyEvent` record -
/// 1.21.1's `keyPressed`/`keyReleased` hand `(int keyCode, int scanCode, int modifiers)` as plain
/// primitives instead. `key()` matches the old record's accessor name so call sites reading
/// `event.key()` keep compiling unchanged. The `isXxx()`/`hasXxxDown()` helpers replicate real
/// 26.1.2 `InputWithModifiers` defaults - GLFW key codes and modifier bit values, unchanged
/// between Minecraft versions, so these are exact, not approximations.
record KeyEvent(int key, int scancode, int modifiers) {
	boolean isSelection() {
		return key == 257 || key == 32 || key == 335;
	}

	boolean isEscape() {
		return key == 256;
	}

	boolean isLeft() {
		return key == 263;
	}

	boolean isRight() {
		return key == 262;
	}

	boolean isUp() {
		return key == 265;
	}

	boolean isDown() {
		return key == 264;
	}

	boolean hasShiftDown() {
		return (modifiers & 1) != 0;
	}

	boolean hasControlDown() {
		return (modifiers & 2) != 0;
	}

	boolean hasAltDown() {
		return (modifiers & 4) != 0;
	}
}
