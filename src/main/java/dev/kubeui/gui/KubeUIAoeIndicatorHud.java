package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

/// Client-side cache of the local player's currently-shown AOE/range indicator (see
/// [KubeUIAoeIndicator]) - pure data, same split every other HUD element in this mod uses.
public final class KubeUIAoeIndicatorHud {
	private static volatile boolean visible = false;
	private static volatile float radius = 0;
	private static volatile int color = 0xFFFFFFFF;

	private KubeUIAoeIndicatorHud() {
	}

	static void receive(CompoundTag data) {
		visible = KubeUINbtCompat.getBooleanOr(data, "visible", false);
		if (visible) {
			radius = KubeUINbtCompat.getFloatOr(data, "radius", 3);
			color = KubeUINbtCompat.getIntOr(data, "color", 0xFFFFFFFF);
		}
	}

	public static boolean visible() {
		return visible;
	}

	public static float radius() {
		return radius;
	}

	public static int color() {
		return color;
	}
}
