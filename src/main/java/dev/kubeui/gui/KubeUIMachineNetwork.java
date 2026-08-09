package dev.kubeui.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// A network of machines linked to a central controller - same real, deliberately
/// simple shape as [KubeUIStorageNetwork]: membership is rebuilt from real
/// `BlockEntity#clearRemoved`/`#setRemoved` as each [KubeUIMachineBlockEntity] loads/unloads, no
/// persisted registry file, so [#status] only ever reports on *currently loaded* members.
final class KubeUIMachineNetwork {
	record Status(String kind, int progress, int processTicks, int energy, int maxEnergy, long crafted) {
	}

	private static final Map<String, Set<KubeUIMachineBlockEntity>> NETWORKS = new ConcurrentHashMap<>();

	private KubeUIMachineNetwork() {
	}

	static void register(String networkId, KubeUIMachineBlockEntity machine) {
		NETWORKS.computeIfAbsent(networkId, ignored -> ConcurrentHashMap.newKeySet()).add(machine);
	}

	static void unregister(KubeUIMachineBlockEntity machine) {
		NETWORKS.values().forEach(members -> members.remove(machine));
	}

	static List<Status> status(String networkId) {
		var result = new ArrayList<Status>();
		for (var machine : NETWORKS.getOrDefault(networkId, Set.of())) {
			result.add(new Status(machine.kind(), machine.progress(), machine.processTicks(), machine.energy(), machine.maxEnergy(), machine.crafted()));
		}
		return result;
	}
}
