package dev.kubeui.gui;

import java.util.ArrayList;
import java.util.List;

/// Non-modal, auto-dismissing, stackable notifications (`KubeUI.toast(message,
/// durationMs)`), independent of whatever KubeUI screen (if any) is currently open. The actual
/// registry lives here (no Minecraft dependency); `KubeUIToastRenderer` (in `dev.kubeui.plugin`,
/// via NeoForge's `RenderGuiEvent.Post`) is what actually draws them every frame, on the HUD
/// layer rather than as part of any one screen.
public final class KubeUIToast {
	private static final List<Entry> ACTIVE = new ArrayList<>();
	private static final int MAX_STACK = 5;

	private KubeUIToast() {
	}

	/// Script-facing (`KubeUI.toast(message, durationMs)`).
	public static void add(String message, int durationMs) {
		if (message == null || message.isEmpty()) {
			return;
		}

		ACTIVE.add(new Entry(message, System.currentTimeMillis() + Math.max(500, durationMs)));

		while (ACTIVE.size() > MAX_STACK) {
			ACTIVE.remove(0);
		}
	}

	/// Removes expired entries and returns whatever's left, oldest first (so new toasts stack
	/// below older ones rather than pushing them around). Called every frame by
	/// `KubeUIToastRenderer` - a different package, hence public.
	public static List<Entry> active() {
		long now = System.currentTimeMillis();
		ACTIVE.removeIf(entry -> entry.expiresAt <= now);
		return ACTIVE;
	}

	public record Entry(String message, long expiresAt) {
	}
}
