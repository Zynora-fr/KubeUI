package dev.kubeui.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Which [KubeUIStorageBlockEntity] position a player currently has open, so the sort/filter/lock/
/// history/network-link actions (all sent as "act on whatever storage I have open" rather than
/// carrying a `BlockPos` themselves) know where to apply - same idea as
/// [KubeUIActions#getOpenScreenId]/`#OPEN_SCREENS`, just for a real block position instead of a
/// KubeUI screen id. Set when [KubeUIStorageBlock#useWithoutItem] opens the menu; never explicitly
/// cleared on close - a stale entry (the player closed the screen and did something else) is
/// harmless, since the action would just silently no-op against a `BlockEntity` no longer actually
/// open/relevant, not act on the wrong one.
final class KubeUIStorageSessions {
	private static final Map<UUID, BlockPos> OPEN = new ConcurrentHashMap<>();

	private KubeUIStorageSessions() {
	}

	static void set(ServerPlayer player, BlockPos pos) {
		OPEN.put(player.getUUID(), pos);
	}

	static BlockPos get(ServerPlayer player) {
		return OPEN.get(player.getUUID());
	}

	static KubeUIStorageBlockEntity currentStorage(ServerPlayer player) {
		var pos = get(player);
		if (pos == null) {
			return null;
		}
		return player.level().getBlockEntity(pos) instanceof KubeUIStorageBlockEntity storage ? storage : null;
	}
}
