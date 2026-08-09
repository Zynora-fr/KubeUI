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
		for (var tag : KubeUINbtCompat.getListOrEmpty(data, "cooldowns")) {
			if (tag instanceof CompoundTag cooldownTag) {
				entries.add(new Entry(
					KubeUINbtCompat.getStringOr(cooldownTag, "id", ""),
					KubeUINbtCompat.getIntOr(cooldownTag, "remaining", 0),
					Math.max(1, KubeUINbtCompat.getIntOr(cooldownTag, "total", 1))
				));
			}
		}
		ACTIVE = List.copyOf(entries);
	}

	public static List<Entry> active() {
		return ACTIVE;
	}
}
