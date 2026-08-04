package dev.kubeui.gui;

import java.lang.ref.WeakReference;

/// Shared debug state: whether widget-bounds outlines are drawn (`/kubeui outline`), and a weak
/// reference to the most recently opened screen, for `/kubeui debug` to report on (a chat command
/// can't inspect the *currently* open screen - typing it closes that screen first).
public final class KubeUIDebug {
	static boolean outlineEnabled = false;
	static boolean gridEnabled = false;
	private static WeakReference<KubeUIScreen> lastOpened = new WeakReference<>(null);

	private KubeUIDebug() {
	}

	static void trackOpened(KubeUIScreen screen) {
		lastOpened = new WeakReference<>(screen);
	}

	static KubeUIScreen lastOpened() {
		return lastOpened.get();
	}

	public static boolean isOutlineEnabled() {
		return outlineEnabled;
	}

	public static void setOutlineEnabled(boolean enabled) {
		outlineEnabled = enabled;
	}

	public static boolean isGridEnabled() {
		return gridEnabled;
	}

	public static void setGridEnabled(boolean enabled) {
		gridEnabled = enabled;
	}

	/// Debug summary of the most recently opened screen, or null if none has been opened yet.
	public static String lastOpenedSummary() {
		var screen = lastOpened();
		return screen != null ? screen.debugSummary() : null;
	}
}
