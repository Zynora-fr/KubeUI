package dev.kubeui.gui;

import net.minecraft.client.Minecraft;

import java.lang.ref.WeakReference;

/// Shared debug state: whether widget-bounds outlines are drawn (`/kubeui outline`), and a weak
/// reference to the most recently opened screen, for `/kubeui debug` to report on (a chat command
/// can't inspect the *currently* open screen - typing it closes that screen first).
public final class KubeUIDebug {
	static boolean outlineEnabled = false;
	static boolean gridEnabled = false;
	private static WeakReference<KubeUIScreen> lastOpened = new WeakReference<>(null);
	private static long lastBuildNanos = -1;
	private static int lastWidgetCount = -1;

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

	/// Called by [KubeUIScreen#rebuild] every time - `/kubeui profile` reports whatever
	/// this last recorded, i.e. the most recent build of whichever screen was rebuilt last
	/// (opening one, switching tabs on one, or a script's `.update(...)` all count).
	static void recordBuild(long nanos, int widgetCount) {
		lastBuildNanos = nanos;
		lastWidgetCount = widgetCount;
	}

	/// Null if no KubeUI screen has built anything yet this session.
	public static String lastProfileSummary() {
		if (lastBuildNanos < 0) {
			return null;
		}
		return String.format("Last screen build: %.2fms, %d widgets", lastBuildNanos / 1_000_000.0, lastWidgetCount);
	}

	private static final int STRESS_SCREENS = 30;
	private static final int STRESS_WIDGETS_PER_SCREEN = 40;

	/// `/kubeui stresstest` - builds and opens `STRESS_SCREENS` synthetic screens of
	/// `STRESS_WIDGETS_PER_SCREEN` labels each, back to back, through the exact same
	/// `Minecraft#setScreen`/[KubeUIScreen#rebuild] path a real screen open goes through (not a
	/// separate simulated path), then restores whatever screen was open before. An on-demand,
	/// in-game load test rather than an automated CI regression check (CI's boot-test never reaches
	/// a point where a screen is actually built, so there's nothing there to measure yet).
	public static String runStressTest() {
		var mc = Minecraft.getInstance();
		var original = mc.screen;

		long start = System.nanoTime();
		for (int i = 0; i < STRESS_SCREENS; i++) {
			var builder = KubeUIScreenBuilder.builder("KubeUI stress test " + i);
			for (int w = 0; w < STRESS_WIDGETS_PER_SCREEN; w++) {
				builder.label("l" + w, "Widget " + w);
			}
			mc.setScreen(new KubeUIScreen(builder));
		}
		long totalNanos = System.nanoTime() - start;

		mc.setScreen(original);

		double totalMs = totalNanos / 1_000_000.0;
		return String.format(
			"Stress test: %d screens x %d widgets = %.2fms total, %.2fms/screen average.",
			STRESS_SCREENS, STRESS_WIDGETS_PER_SCREEN, totalMs, totalMs / STRESS_SCREENS
		);
	}
}
