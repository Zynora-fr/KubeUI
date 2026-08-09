package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/// Client-side cache of the local player's currently-active scriptable cooldowns (see
/// [KubeUICooldowns]) - pure data, same split every other HUD element in this mod uses.
public final class KubeUICooldownHud {
	public record Entry(String id, int remaining, int total) {
	}

	private static volatile List<Entry> ACTIVE = List.of();

	private KubeUICooldownHud() {
	}

	static void receive(CompoundTag data) {
		var entries = new ArrayList<Entry>();
		for (var tag : data.getListOrEmpty("cooldowns")) {
			if (tag instanceof CompoundTag cooldownTag) {
				entries.add(new Entry(
					cooldownTag.getStringOr("id", ""),
					cooldownTag.getIntOr("remaining", 0),
					Math.max(1, cooldownTag.getIntOr("total", 1))
				));
			}
		}
		ACTIVE = List.copyOf(entries);
	}

	public static List<Entry> active() {
		return ACTIVE;
	}
}
