package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Scriptable skill/item cooldown bars in HUD (`KubeUIActions.startCooldown(player, id,
/// durationTicks)`) - deliberately independent of real vanilla `Player#getCooldowns()`
/// (`ItemCooldowns`, which only ever tracks *items*) since "a future active-skill system" (the real
/// motivation named for this one) has no physical item to key a vanilla cooldown off in the first
/// place - a purely scriptable id instead.
public final class KubeUICooldowns {
	/// player uuid -> (cooldown id -> {remaining, total})
	private static final Map<UUID, Map<String, int[]>> ACTIVE = new ConcurrentHashMap<>();

	private KubeUICooldowns() {
	}

	static void start(ServerPlayer player, String id, int durationTicks) {
		if (durationTicks <= 0) {
			return;
		}
		ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>()).put(id, new int[]{durationTicks, durationTicks});
		sync(player);
	}

	static boolean isActive(ServerPlayer player, String id) {
		var active = ACTIVE.get(player.getUUID());
		return active != null && active.containsKey(id);
	}

	static int remaining(ServerPlayer player, String id) {
		var active = ACTIVE.get(player.getUUID());
		var entry = active != null ? active.get(id) : null;
		return entry != null ? entry[0] : 0;
	}

	static void clear(ServerPlayer player, String id) {
		var active = ACTIVE.get(player.getUUID());
		if (active != null && active.remove(id) != null) {
			sync(player);
		}
	}

	/// Called once per real server tick, per player (see `KubeUICombatTickEvents`).
	public static void tick(ServerPlayer player) {
		var active = ACTIVE.get(player.getUUID());
		if (active == null || active.isEmpty()) {
			return;
		}
		boolean changed = active.values().removeIf(entry -> --entry[0] <= 0);
		if (changed) {
			sync(player);
		}
	}

	private static void sync(ServerPlayer player) {
		var active = ACTIVE.getOrDefault(player.getUUID(), Map.of());
		var listTag = new ListTag();
		for (var entry : active.entrySet()) {
			var tag = new CompoundTag();
			tag.putString("id", entry.getKey());
			tag.putInt("remaining", entry.getValue()[0]);
			tag.putInt("total", entry.getValue()[1]);
			listTag.add(tag);
		}
		var data = new CompoundTag();
		data.put("cooldowns", listTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.COOLDOWN_UPDATE_SCREEN_ID, data));
	}
}
