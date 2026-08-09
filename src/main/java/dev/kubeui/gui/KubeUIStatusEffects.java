package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Purely scriptable statuses beyond vanilla `MobEffect` (`KubeUIActions.defineStatus(id, name,
/// icon)`/`.applyStatus(...)`/`.removeStatus(...)`) - a status here has no built-in game-logic of
/// its own at all (no attribute modifiers, no particles), unlike a real `MobEffect` - its "own
/// icon/tick logic" is entirely a script's own `KubeUICombatEvents.statusTick(event => {...})`
/// handler (see [KubeUICombatEvents]), fired once per status per real server tick while active.
/// This is the honest trade-off for not needing a real registered `MobEffect`/`Holder<MobEffect>`
/// per script-defined status, which - like a machine "kind" - isn't something a `server_scripts`
/// reload can register after the real registries are already frozen.
public final class KubeUIStatusEffects {
	record Def(String id, String name, String icon) {
	}

	private static final Map<String, Def> DEFS = new ConcurrentHashMap<>();
	/// player uuid -> (statusId -> remaining ticks, `-1` = infinite)
	private static final Map<UUID, Map<String, Integer>> ACTIVE = new ConcurrentHashMap<>();

	private KubeUIStatusEffects() {
	}

	static void define(String id, String name, String icon) {
		DEFS.put(id, new Def(id, name, icon == null ? "" : icon));
	}

	static Def def(String id) {
		return DEFS.get(id);
	}

	static void apply(ServerPlayer player, String statusId, int durationTicks) {
		if (!DEFS.containsKey(statusId)) {
			return;
		}
		ACTIVE.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>()).put(statusId, durationTicks);
		sync(player);
	}

	static void remove(ServerPlayer player, String statusId) {
		var active = ACTIVE.get(player.getUUID());
		if (active != null && active.remove(statusId) != null) {
			sync(player);
		}
	}

	static boolean has(ServerPlayer player, String statusId) {
		var active = ACTIVE.get(player.getUUID());
		return active != null && active.containsKey(statusId);
	}

	/// Called once per real server tick, per player (see `KubeUICombatTickEvents`, driven by a real
	/// `PlayerTickEvent.Post`) - decrements every status active on `player`, drops expired ones, and
	/// fires [KubeUICombatEvents#postStatusTick] for whatever's still running so a script's own tick
	/// logic (damage-over-time, a stacking buff, ...) actually runs.
	public static void tick(ServerPlayer player) {
		var active = ACTIVE.get(player.getUUID());
		if (active == null || active.isEmpty()) {
			return;
		}

		boolean changed = false;
		var iterator = active.entrySet().iterator();
		while (iterator.hasNext()) {
			var statusEntry = iterator.next();
			int remaining = statusEntry.getValue();
			if (remaining == 0) {
				iterator.remove();
				changed = true;
				continue;
			}
			KubeUICombatEvents.postStatusTick(player, statusEntry.getKey(), remaining);
			if (remaining > 0) {
				statusEntry.setValue(remaining - 1);
			}
		}
		if (changed) {
			sync(player);
		}
	}

	private static void sync(ServerPlayer player) {
		var active = ACTIVE.getOrDefault(player.getUUID(), Map.of());
		var listTag = new ListTag();
		for (var entry : active.entrySet()) {
			var def = DEFS.get(entry.getKey());
			if (def == null) {
				continue;
			}
			var tag = new CompoundTag();
			tag.putString("id", def.id());
			tag.putString("name", def.name());
			tag.putString("icon", def.icon());
			tag.putInt("remaining", entry.getValue());
			listTag.add(tag);
		}
		var data = new CompoundTag();
		data.put("statuses", listTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.STATUS_UPDATE_SCREEN_ID, data));
	}
}
