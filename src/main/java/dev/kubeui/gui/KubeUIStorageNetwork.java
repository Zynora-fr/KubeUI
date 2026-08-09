package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// A simple network of linked containers - deliberately the honest, reduced scope the
/// roadmap entry itself invited ("inspiré des mods de stockage type AE2, volontairement plus
/// simple"). No persisted registry file: membership is rebuilt implicitly from real
/// [net.minecraft.world.level.block.entity.BlockEntity#clearRemoved]/`#setRemoved` (a block entity
/// registers itself here once loaded, unregisters once removed/unloaded - see
/// [KubeUIStorageBlockEntity]) rather than a second, disk-backed source of truth to keep in sync
/// with the real one. The real, honest consequence: [#aggregate] only ever sees *currently loaded*
/// members - a container in an unloaded chunk contributes nothing to the total until its chunk
/// loads again, same as it would be unreadable either way even with a persisted position list (its
/// block entity wouldn't be in memory to read from).
final class KubeUIStorageNetwork {
	private static final Map<String, Set<KubeUIStorageBlockEntity>> NETWORKS = new ConcurrentHashMap<>();

	private KubeUIStorageNetwork() {
	}

	static void register(String networkId, KubeUIStorageBlockEntity storage) {
		NETWORKS.computeIfAbsent(networkId, ignored -> ConcurrentHashMap.newKeySet()).add(storage);
	}

	static void unregister(KubeUIStorageBlockEntity storage) {
		NETWORKS.values().forEach(members -> members.remove(storage));
	}

	/// Item id -> total count, summed across every currently-loaded member of `networkId`.
	static Map<String, Integer> aggregate(String networkId) {
		var totals = new LinkedHashMap<String, Integer>();
		for (var storage : NETWORKS.getOrDefault(networkId, Set.of())) {
			for (int i = 0; i < storage.getContainerSize(); i++) {
				var stack = storage.getItem(i);
				if (stack.isEmpty()) {
					continue;
				}
				var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (key != null) {
					totals.merge(key.toString(), stack.getCount(), Integer::sum);
				}
			}
		}
		return totals;
	}

	/// Whether `player` is the owner/an authorized player on *any* currently-loaded member of
	/// `networkId` - backs the remote preview ("if the player has permanent access").
	static boolean hasAccess(String networkId, java.util.UUID playerId) {
		for (var storage : NETWORKS.getOrDefault(networkId, Set.of())) {
			if (playerId.equals(storage.owner()) || storage.authorizedPlayers().contains(playerId)) {
				return true;
			}
		}
		return false;
	}
}
