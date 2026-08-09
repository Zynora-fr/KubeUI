package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/// Client-side cache of the local player's recent combat-log lines (see [KubeUICombatLog]) - pure
/// data, same split every other HUD element in this mod uses. Each receive replaces the whole list
/// (server already caps/orders it) rather than appending - simpler than reconciling a diff for a
/// handful of short-lived lines.
public final class KubeUICombatLogHud {
	private static volatile List<String> LINES = List.of();
	private static volatile long lastUpdateAt = 0;

	private KubeUICombatLogHud() {
	}

	static void receive(CompoundTag data) {
		var lines = new ArrayList<String>();
		for (var tag : data.getListOrEmpty("lines")) {
			if (tag instanceof CompoundTag lineTag) {
				lines.add(lineTag.getStringOr("line", ""));
			}
		}
		LINES = List.copyOf(lines);
		lastUpdateAt = System.currentTimeMillis();
	}

	public static List<String> lines() {
		return LINES;
	}

	/// Lets the renderer fade the whole log out a few seconds after the last hit, rather than it
	/// permanently sitting on screen once a fight's long over.
	public static long lastUpdateAt() {
		return lastUpdateAt;
	}
}
