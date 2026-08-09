package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Live presence status ("online"/"in_combat"/"in_dungeon"/a script-defined custom string) visible
/// to friends/party (`KubeUIActions.presenceOf(player)`) - purely in-memory/session-scoped, computed
/// on read rather than pushed: `"in_combat"` reuses [KubeUICombatLog]'s own real active-session
/// check, everything else is whatever a script last set via `KubeUIActions.setPresence(...)` (a
/// dungeon script calling this on room-enter/room-leave, for instance) - no separate "is this player
/// in a dungeon" inference logic duplicated here.
final class KubeUIPresence {
	private static final Map<UUID, String> CUSTOM_STATUS = new ConcurrentHashMap<>();

	private KubeUIPresence() {
	}

	static void set(ServerPlayer player, String status) {
		if (status == null || status.isBlank() || "online".equals(status)) {
			CUSTOM_STATUS.remove(player.getUUID());
		} else {
			CUSTOM_STATUS.put(player.getUUID(), status);
		}
	}

	static String of(ServerPlayer player) {
		if (KubeUICombatLog.hasActiveSession(player)) {
			return "in_combat";
		}
		return CUSTOM_STATUS.getOrDefault(player.getUUID(), "online");
	}
}
