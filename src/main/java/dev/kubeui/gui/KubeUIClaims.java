package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Individual land claims (`KubeUIActions.claimLand(...)`) - "même esprit que" [KubeUIGuildTerritory]
/// but owned by one player instead of a guild, plus real member/permission/visit/rental machinery a
/// guild territory doesn't need. Disk-persisted (same real mechanism [KubeUIGuilds]'s own ledger
/// already established) since a claim, like a guild, is meant to outlive a session.
public final class KubeUIClaims {
	static final class Claim {
		final String id;
		UUID owner;
		double x1, y1, z1, x2, y2, z2;
		String dimension;
		final Set<UUID> members = new LinkedHashSet<>();
		final Map<String, Boolean> permissions = new ConcurrentHashMap<>();
		final Map<UUID, Long> tempVisitors = new ConcurrentHashMap<>();
		final Map<UUID, Long> renters = new ConcurrentHashMap<>();

		Claim(String id, UUID owner, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
			this.id = id;
			this.owner = owner;
			this.x1 = x1;
			this.y1 = y1;
			this.z1 = z1;
			this.x2 = x2;
			this.y2 = y2;
			this.z2 = z2;
			this.dimension = dimension;
		}

		boolean contains(double x, double y, double z, String dim) {
			return dimension.equals(dim)
				&& x >= Math.min(x1, x2) && x <= Math.max(x1, x2)
				&& y >= Math.min(y1, y2) && y <= Math.max(y1, y2)
				&& z >= Math.min(z1, z2) && z <= Math.max(z1, z2);
		}

		double sizeBlocks() {
			return Math.abs(x2 - x1) * Math.abs(z2 - z1);
		}

		boolean hasAccess(UUID playerId) {
			long now = System.currentTimeMillis();
			return playerId.equals(owner) || members.contains(playerId)
				|| tempVisitors.getOrDefault(playerId, 0L) > now
				|| renters.getOrDefault(playerId, 0L) > now;
		}
	}

	private static final Map<String, Claim> CLAIMS = new ConcurrentHashMap<>();
	private static final Map<UUID, Set<String>> CURRENTLY_INSIDE = new ConcurrentHashMap<>();
	private static int maxClaimsPerPlayer = 3;
	private static double maxClaimSizeBlocks = 10_000;
	private static MinecraftServer serverRef;
	private static volatile boolean dirty;

	private KubeUIClaims() {
	}

	static void setLimits(int maxClaims, double maxSizeBlocks) {
		maxClaimsPerPlayer = Math.max(1, maxClaims);
		maxClaimSizeBlocks = Math.max(1, maxSizeBlocks);
	}

	private static long ownedCount(UUID owner) {
		return CLAIMS.values().stream().filter(c -> c.owner.equals(owner)).count();
	}

	/// Fails cleanly (`false`) if `owner` already has [#maxClaimsPerPlayer] claims, the requested
	/// area exceeds [#maxClaimSizeBlocks], or `claimId` is already taken.
	static boolean claim(ServerPlayer owner, String claimId, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		if (CLAIMS.containsKey(claimId) || ownedCount(owner.getUUID()) >= maxClaimsPerPlayer) {
			return false;
		}
		double size = Math.abs(x2 - x1) * Math.abs(z2 - z1);
		if (size > maxClaimSizeBlocks) {
			return false;
		}
		CLAIMS.put(claimId, new Claim(claimId, owner.getUUID(), x1, y1, z1, x2, y2, z2, dimension));
		dirty = true;
		return true;
	}

	static Claim get(String claimId) {
		return CLAIMS.get(claimId);
	}

	static int count() {
		return CLAIMS.size();
	}

	/// Every claim `owner` owns - backs a real "/claims"-style listing command ("une commande pour
	/// voir les claims" - real ask), not just the single-claim [#claimAt]/[#get] lookups this class
	/// already had.
	static List<Claim> ownedBy(UUID owner) {
		return CLAIMS.values().stream().filter(c -> c.owner.equals(owner)).toList();
	}

	static Claim claimAt(double x, double y, double z, String dimension) {
		for (var claim : CLAIMS.values()) {
			if (claim.contains(x, y, z, dimension)) {
				return claim;
			}
		}
		return null;
	}

	private static boolean isOwner(ServerPlayer player, Claim claim) {
		return claim != null && claim.owner.equals(player.getUUID());
	}

	static boolean addMember(ServerPlayer owner, String claimId, UUID memberId) {
		var claim = CLAIMS.get(claimId);
		if (!isOwner(owner, claim)) {
			return false;
		}
		dirty |= claim.members.add(memberId);
		return true;
	}

	static boolean removeMember(ServerPlayer owner, String claimId, UUID memberId) {
		var claim = CLAIMS.get(claimId);
		if (!isOwner(owner, claim)) {
			return false;
		}
		dirty |= claim.members.remove(memberId);
		return true;
	}

	static boolean setPermission(ServerPlayer owner, String claimId, String action, boolean allowed) {
		var claim = CLAIMS.get(claimId);
		if (!isOwner(owner, claim)) {
			return false;
		}
		claim.permissions.put(action, allowed);
		dirty = true;
		return true;
	}

	static boolean permissionAllowed(String claimId, String action, boolean defaultValue) {
		var claim = CLAIMS.get(claimId);
		return claim == null || claim.permissions.getOrDefault(action, defaultValue);
	}

	/// Temporary, revocable access - `durationTicks` from now, real-time-based (not tied to the
	/// game clock) so it still expires correctly across a restart.
	static boolean inviteVisit(ServerPlayer owner, String claimId, UUID targetId, long durationTicks) {
		var claim = CLAIMS.get(claimId);
		if (!isOwner(owner, claim)) {
			return false;
		}
		claim.tempVisitors.put(targetId, System.currentTimeMillis() + durationTicks * 50L);
		dirty = true;
		return true;
	}

	static boolean revokeVisit(ServerPlayer owner, String claimId, UUID targetId) {
		var claim = CLAIMS.get(claimId);
		if (!isOwner(owner, claim)) {
			return false;
		}
		dirty |= claim.tempVisitors.remove(targetId) != null;
		return true;
	}

	/// Charges `renterId` up front (real [KubeUICurrency], atomic - fails cleanly if they can't
	/// afford it) then grants them member-shaped access until `durationTicks` from now.
	static boolean rent(ServerPlayer owner, String claimId, ServerPlayer renter, String currency, long price, long durationTicks) {
		var claim = CLAIMS.get(claimId);
		if (!isOwner(owner, claim) || !KubeUICurrency.charge(renter, currency, price)) {
			return false;
		}
		KubeUICurrency.pay(owner, currency, price);
		claim.renters.put(renter.getUUID(), System.currentTimeMillis() + durationTicks * 50L);
		dirty = true;
		return true;
	}

	/// Merges `claimId2` into `claimId1` as their combined bounding box (a real, deliberately
	/// simple "smallest box containing both" merge - not a strict adjacency check, an honest reduced
	/// scope) - `claimId2` is removed. Both must share the same owner.
	static boolean merge(ServerPlayer owner, String claimId1, String claimId2) {
		var a = CLAIMS.get(claimId1);
		var b = CLAIMS.get(claimId2);
		if (!isOwner(owner, a) || !isOwner(owner, b) || !a.dimension.equals(b.dimension)) {
			return false;
		}
		a.x1 = Math.min(Math.min(a.x1, a.x2), Math.min(b.x1, b.x2));
		a.x2 = Math.max(Math.max(a.x1, a.x2), Math.max(b.x1, b.x2));
		a.z1 = Math.min(Math.min(a.z1, a.z2), Math.min(b.z1, b.z2));
		a.z2 = Math.max(Math.max(a.z1, a.z2), Math.max(b.z1, b.z2));
		a.y1 = Math.min(a.y1, b.y1);
		a.y2 = Math.max(a.y2, b.y2);
		CLAIMS.remove(claimId2);
		dirty = true;
		return true;
	}

	/// Splits `claimId` along the X or Z axis at `coordinate` into two new claims (`newId1` gets
	/// the lower half, `newId2` the upper half) - the original id is removed.
	static boolean split(ServerPlayer owner, String claimId, String axis, double coordinate, String newId1, String newId2) {
		var claim = CLAIMS.get(claimId);
		if (!isOwner(owner, claim) || CLAIMS.containsKey(newId1) || CLAIMS.containsKey(newId2)) {
			return false;
		}
		boolean xAxis = "x".equalsIgnoreCase(axis);
		double lo = xAxis ? Math.min(claim.x1, claim.x2) : Math.min(claim.z1, claim.z2);
		double hi = xAxis ? Math.max(claim.x1, claim.x2) : Math.max(claim.z1, claim.z2);
		if (coordinate <= lo || coordinate >= hi) {
			return false;
		}

		Claim lower, upper;
		if (xAxis) {
			lower = new Claim(newId1, owner.getUUID(), lo, claim.y1, Math.min(claim.z1, claim.z2), coordinate, claim.y2, Math.max(claim.z1, claim.z2), claim.dimension);
			upper = new Claim(newId2, owner.getUUID(), coordinate, claim.y1, Math.min(claim.z1, claim.z2), hi, claim.y2, Math.max(claim.z1, claim.z2), claim.dimension);
		} else {
			lower = new Claim(newId1, owner.getUUID(), Math.min(claim.x1, claim.x2), claim.y1, lo, Math.max(claim.x1, claim.x2), claim.y2, coordinate, claim.dimension);
			upper = new Claim(newId2, owner.getUUID(), Math.min(claim.x1, claim.x2), claim.y1, coordinate, Math.max(claim.x1, claim.x2), claim.y2, hi, claim.dimension);
		}
		CLAIMS.remove(claimId);
		CLAIMS.put(newId1, lower);
		CLAIMS.put(newId2, upper);
		dirty = true;
		return true;
	}

	/// Called every [KubeUIClaimEvents#CHECK_INTERVAL_TICKS], per player - toasts the owner (never
	/// the visitor) once per entry, only for a player with no real access.
	public static void check(ServerPlayer player) {
		String dimension = player.level().dimension().identifier().toString();
		var here = new LinkedHashSet<String>();
		for (var claim : CLAIMS.values()) {
			if (claim.contains(player.getX(), player.getY(), player.getZ(), dimension)) {
				here.add(claim.id);
			}
		}

		var wasInside = CURRENTLY_INSIDE.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashSet<>());
		for (var claimId : here) {
			if (wasInside.contains(claimId)) {
				continue;
			}
			var claim = CLAIMS.get(claimId);
			if (claim != null && !claim.hasAccess(player.getUUID())) {
				var ownerPlayer = serverRef != null ? serverRef.getPlayerList().getPlayer(claim.owner) : null;
				if (ownerPlayer != null) {
					KubeUIMachineAlerts.send(ownerPlayer, player.getGameProfile().name() + " entered your claim \"" + claim.id + "\"");
				}
			}
		}
		wasInside.clear();
		wasInside.addAll(here);
	}

	// ---------------------------------------------------------------- persistence

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("kubeui_claims.dat");
	}

	static void load(MinecraftServer server) {
		serverRef = server;
		CLAIMS.clear();
		dirty = false;

		var path = file(server);
		if (!Files.exists(path)) {
			return;
		}
		try {
			var root = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			for (var entry : root.getListOrEmpty("claims")) {
				if (!(entry instanceof CompoundTag tag)) {
					continue;
				}
				var claim = new Claim(
					tag.getStringOr("id", ""), UUID.fromString(tag.getStringOr("owner", "")),
					tag.getDoubleOr("x1", 0), tag.getDoubleOr("y1", 0), tag.getDoubleOr("z1", 0),
					tag.getDoubleOr("x2", 0), tag.getDoubleOr("y2", 0), tag.getDoubleOr("z2", 0),
					tag.getStringOr("dimension", "minecraft:overworld")
				);
				for (var m : tag.getListOrEmpty("members")) {
					if (m instanceof net.minecraft.nbt.StringTag s) {
						claim.members.add(UUID.fromString(s.value()));
					}
				}
				var permsTag = tag.getCompoundOrEmpty("permissions");
				for (var key : permsTag.keySet()) {
					permsTag.getBoolean(key).ifPresent(v -> claim.permissions.put(key, v));
				}
				CLAIMS.put(claim.id, claim);
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load claims from disk", ex);
		}
	}

	static boolean isDirty() {
		return dirty;
	}

	static void save() {
		if (serverRef == null) {
			return;
		}
		dirty = false;

		var root = new CompoundTag();
		var claimsTag = new ListTag();
		for (var claim : CLAIMS.values()) {
			var tag = new CompoundTag();
			tag.putString("id", claim.id);
			tag.putString("owner", claim.owner.toString());
			tag.putDouble("x1", claim.x1);
			tag.putDouble("y1", claim.y1);
			tag.putDouble("z1", claim.z1);
			tag.putDouble("x2", claim.x2);
			tag.putDouble("y2", claim.y2);
			tag.putDouble("z2", claim.z2);
			tag.putString("dimension", claim.dimension);

			var membersTag = new ListTag();
			for (var member : claim.members) {
				membersTag.add(net.minecraft.nbt.StringTag.valueOf(member.toString()));
			}
			tag.put("members", membersTag);

			var permsTag = new CompoundTag();
			for (var perm : claim.permissions.entrySet()) {
				permsTag.putBoolean(perm.getKey(), perm.getValue());
			}
			tag.put("permissions", permsTag);

			claimsTag.add(tag);
		}
		root.put("claims", claimsTag);

		try {
			var path = file(serverRef);
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save claims to disk", ex);
		}
	}
}
