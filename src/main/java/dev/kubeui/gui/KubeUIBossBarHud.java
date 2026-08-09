package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;

/// Client-side cache of every currently-shown boss bar (see [KubeUIBossBar]) - pure data, no
/// Minecraft rendering dependency, same split [KubeUIQuestHud] already uses versus its own
/// renderer. A `LinkedHashMap` so multiple simultaneous bars stack in the order they were first
/// shown, not an arbitrary hash order.
public final class KubeUIBossBarHud {
	public record Entry(String barId, String name, float health, float maxHealth, int color, String phaseText) {
	}

	private static final Map<String, Entry> ACTIVE = new LinkedHashMap<>();

	private KubeUIBossBarHud() {
	}

	static void receive(CompoundTag data) {
		String barId = data.getStringOr("barId", "");
		if (barId.isEmpty()) {
			return;
		}
		if (data.getBooleanOr("hide", false)) {
			ACTIVE.remove(barId);
			return;
		}

		ACTIVE.put(barId, new Entry(
			barId,
			data.getStringOr("name", barId),
			data.getFloatOr("health", 0),
			data.getFloatOr("maxHealth", 1),
			data.getIntOr("color", 0xFFCC3333),
			data.getStringOr("phaseText", "")
		));
	}

	public static java.util.Collection<Entry> active() {
		return ACTIVE.values();
	}
}
