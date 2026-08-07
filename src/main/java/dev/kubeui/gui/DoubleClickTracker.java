package dev.kubeui.gui;

import net.minecraft.Util;

/// 1.21.1's `AbstractWidget` has no built-in double-click detection (26.1.2's `onClick(event,
/// boolean doubleClick)` computes it automatically before calling the widget). Each widget with
/// an `onClick(MouseButtonEvent, boolean)` override owns one of these and calls
/// [#registerClick(double, double)] from its real `onClick(double, double, int)` bridge to
/// recover the same signal - same 250ms/same-position threshold vanilla itself uses elsewhere.
final class DoubleClickTracker {
	private static final long THRESHOLD_MS = 250L;
	private static final double MAX_DISTANCE_SQ = 4.0 * 4.0;

	private long lastClickTime = -1;
	private double lastX;
	private double lastY;

	boolean registerClick(double x, double y) {
		long now = Util.getMillis();
		boolean isDoubleClick = lastClickTime >= 0
			&& now - lastClickTime < THRESHOLD_MS
			&& distanceSq(x, y) <= MAX_DISTANCE_SQ;
		lastClickTime = isDoubleClick ? -1 : now;
		lastX = x;
		lastY = y;
		return isDoubleClick;
	}

	private double distanceSq(double x, double y) {
		double dx = x - lastX;
		double dy = y - lastY;
		return dx * dx + dy * dy;
	}
}
