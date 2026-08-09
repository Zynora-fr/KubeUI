package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Script-defined structure zones (`KubeUIActions.defineStructure(id, name, difficulty, x1,y1,z1,
/// x2,y2,z2, dimension)`) - a real vanilla `Structure`/`StructureManager` lookup only ever tells you
/// about *generated* structures already known to a chunk, nothing for a hand-built or datapacked one
/// with no `Structure` registration of its own, so this is a plain scriptable bounding box instead,
/// same "real but reduced scope" trade-off [KubeUIMachineBlockEntity]'s own single-input-recipe
/// already accepted. Checked periodically (`KubeUIStructureZoneEvents`, real server-side
/// `PlayerTickEvent.Post`, same mechanism [KubeUIMapClientEvents] already uses client-side for its
/// own periodic per-player check) rather than every tick - a structure's info popup doesn't need
/// sub-second precision. Opt-in per player (`KubeUIActions.setStructureInfoEnabled(...)`) so a
/// player who doesn't want popups can turn them off entirely.
public final class KubeUIStructureZones {
	record Zone(String id, String name, String difficulty, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		boolean contains(double x, double y, double z, String dim) {
			return dimension.equals(dim)
				&& x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
				&& y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
				&& z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
		}
	}

	private static final Map<String, Zone> ZONES = new ConcurrentHashMap<>();
	private static final Map<UUID, Boolean> OPTED_IN = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<String>> CURRENTLY_INSIDE = new ConcurrentHashMap<>();

	private KubeUIStructureZones() {
	}

	static void define(String id, String name, String difficulty, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		ZONES.put(id, new Zone(id, name, difficulty, x1, y1, z1, x2, y2, z2, dimension));
	}

	static void setEnabled(ServerPlayer player, boolean enabled) {
		OPTED_IN.put(player.getUUID(), enabled);
	}

	/// Called every [KubeUIStructureZoneEvents#CHECK_INTERVAL_TICKS] ticks, per player.
	public static void check(ServerPlayer player) {
		if (!OPTED_IN.getOrDefault(player.getUUID(), true)) {
			return;
		}

		String dimension = player.level().dimension().identifier().toString();
		var inside = CURRENTLY_INSIDE.computeIfAbsent(player.getUUID(), ignored -> new java.util.HashSet<>());
		var stillInside = new java.util.HashSet<String>();

		for (var zone : ZONES.values()) {
			if (zone.contains(player.getX(), player.getY(), player.getZ(), dimension)) {
				stillInside.add(zone.id());
				if (!inside.contains(zone.id())) {
					announce(player, zone);
				}
			}
		}
		inside.clear();
		inside.addAll(stillInside);
	}

	/// Reuses the real toast pipeline [KubeUIMachineAlerts] already established (one more
	/// server-push message, same [KubeUIToast] rendering) rather than a dedicated screen/payload -
	/// "affiché à l'approche d'une structure" is exactly what a toast already is.
	private static void announce(ServerPlayer player, Zone zone) {
		double distance = Math.sqrt(player.distanceToSqr(
			(zone.x1() + zone.x2()) / 2, (zone.y1() + zone.y2()) / 2, (zone.z1() + zone.z2()) / 2
		));
		KubeUIMachineAlerts.send(player, "Entering " + zone.name() + " (" + zone.difficulty() + ", " + (int) distance + "m)");
	}
}
