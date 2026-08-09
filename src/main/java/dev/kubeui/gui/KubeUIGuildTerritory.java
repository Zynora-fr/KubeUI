package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// A basic guild claim (`KubeUIActions.claimGuildTerritory(guildId, x1,y1,z1,x2,y2,z2,
/// dimension)`) - a plain scriptable bounding box owned by a guild, same real shape
/// [KubeUIStructureZones] already uses for its own zones, plus a toast when a *non-member* enters
/// one. Real anti-grief protection (actually blocking break/place inside the zone) is deliberately
/// not attempted here - that's a real, substantial feature of its own, noted as the genuine
/// integration point a script (or the future [dev.kubeui.gui.KubeUIPermissions] hook) can build on,
/// not a reimplementation of a dedicated claims mod.
public final class KubeUIGuildTerritory {
	record Claim(String guildId, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		boolean contains(double x, double y, double z, String dim) {
			return dimension.equals(dim)
				&& x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
				&& y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
				&& z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
		}
	}

	private static final Map<String, Claim> CLAIMS = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<String>> CURRENTLY_INSIDE = new ConcurrentHashMap<>();

	private KubeUIGuildTerritory() {
	}

	static void claim(String guildId, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		CLAIMS.put(guildId, new Claim(guildId, x1, y1, z1, x2, y2, z2, dimension));
	}

	static String territoryAt(double x, double y, double z, String dimension) {
		for (var claim : CLAIMS.values()) {
			if (claim.contains(x, y, z, dimension)) {
				return claim.guildId();
			}
		}
		return null;
	}

	/// Called every [KubeUIStructureZoneEvents#CHECK_INTERVAL_TICKS], per player (see
	/// [KubeUIGuildTerritoryEvents]) - toasts the player once per entry, only when they're not
	/// already a member of the guild whose land it is.
	public static void check(ServerPlayer player) {
		String dimension = player.level().dimension().identifier().toString();
		String claimId = territoryAt(player.getX(), player.getY(), player.getZ(), dimension);
		var inside = CURRENTLY_INSIDE.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());

		if (claimId == null) {
			inside.clear();
			return;
		}
		if (inside.contains(claimId)) {
			return;
		}
		inside.clear();
		inside.add(claimId);

		var guild = KubeUIGuilds.get(claimId);
		if (guild == null) {
			return;
		}
		boolean isMember = guild.members.containsKey(player.getUUID());
		if (!isMember) {
			KubeUIMachineAlerts.send(player, "Entering " + guild.name + "'s territory");
		}
	}
}
