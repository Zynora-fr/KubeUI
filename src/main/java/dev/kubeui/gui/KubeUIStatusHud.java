package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/// Client-side cache of the local player's currently-active custom statuses (see
/// [KubeUIStatusEffects]) - pure data, no Minecraft rendering dependency, same split every other
/// HUD element in this mod already uses. Real vanilla `MobEffect`s need no equivalent cache at all -
/// `KubeUIStatusOverlayHudRenderer` reads `Player#getActiveEffects()` directly, live, every frame.
public final class KubeUIStatusHud {
	public record Entry(String id, String name, String icon, int remaining) {
	}

	private static volatile List<Entry> ACTIVE = List.of();

	private KubeUIStatusHud() {
	}

	static void receive(CompoundTag data) {
		var entries = new ArrayList<Entry>();
		for (var tag : data.getListOrEmpty("statuses")) {
			if (tag instanceof CompoundTag statusTag) {
				entries.add(new Entry(
					statusTag.getStringOr("id", ""),
					statusTag.getStringOr("name", ""),
					statusTag.getStringOr("icon", ""),
					statusTag.getIntOr("remaining", 0)
				));
			}
		}
		ACTIVE = List.copyOf(entries);
	}

	public static List<Entry> active() {
		return ACTIVE;
	}
}
