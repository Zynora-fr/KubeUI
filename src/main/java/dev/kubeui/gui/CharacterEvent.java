package dev.kubeui.gui;

/// Compatibility shim mirroring 26.1.2's real `net.minecraft.client.input.CharacterEvent` record -
/// 1.21.1's `charTyped` hands `(char codePoint, int modifiers)` as plain primitives instead.
record CharacterEvent(int codepoint) {
}
