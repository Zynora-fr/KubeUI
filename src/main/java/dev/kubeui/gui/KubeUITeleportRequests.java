package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// A `/kubeui tpa`-like request with a real confirmation step (`KubeUIActions.requestTeleport(from,
/// to)`) - unlike a raw teleport command a player could fire off with no warning, the target has to
/// actually accept (`.acceptTeleport(target)`) before anything moves; a script builds the actual
/// `KubeUI.confirm()` dialog client-side off a real server-pushed notification, this class only
/// owns the pending-request state and the real `ServerPlayer#teleportTo` call once accepted.
/// Requests expire after [#REQUEST_TIMEOUT_MS] so an old, forgotten request can't be accepted days
/// later out of nowhere.
final class KubeUITeleportRequests {
	private static final long REQUEST_TIMEOUT_MS = 60_000;

	private record Pending(UUID fromId, long expiresAt) {
	}

	private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

	private KubeUITeleportRequests() {
	}

	static void request(ServerPlayer from, ServerPlayer to) {
		PENDING.put(to.getUUID(), new Pending(from.getUUID(), System.currentTimeMillis() + REQUEST_TIMEOUT_MS));
	}

	/// Teleports the requester to `target` if a real, still-valid pending request exists - returns
	/// the requester's name on success (so the caller can announce it), `""` otherwise.
	static String accept(ServerPlayer target) {
		var pending = PENDING.remove(target.getUUID());
		if (pending == null || pending.expiresAt() < System.currentTimeMillis()) {
			return "";
		}
		var requester = target.level().getServer().getPlayerList().getPlayer(pending.fromId());
		if (requester == null || !(target.level() instanceof net.minecraft.server.level.ServerLevel targetLevel)) {
			return "";
		}
		requester.teleportTo(targetLevel, target.getX(), target.getY(), target.getZ(), java.util.Set.of(), requester.getYRot(), requester.getXRot());
		return requester.getGameProfile().getName();
	}

	static void deny(ServerPlayer target) {
		PENDING.remove(target.getUUID());
	}
}
