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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Guild/faction data model (`KubeUIActions.createGuild(...)`) - membership, role hierarchy
/// (`"owner"`/`"officer"`/`"member"`), alliances/rivalries between guilds, collective XP/level, and
/// a badge (color + icon, resolved exactly like a skill node's own `icon`) - all real, server-
/// authoritative, disk-persisted (compressed NBT under `<world>/data/`, same real mechanism
/// [KubeUICurrency]'s own ledger already uses, since a guild is meant to outlive a single session
/// unlike, say, a [KubeUIParties] temporary group). One real registry backs several roadmap entries
/// at once - membership/roles, alliances, leveling, and the badge fields are all just different
/// columns on the same real `Guild` record, the same "one system, several angles" reasoning already
/// used for [KubeUIMachineBridge]'s combined network/stats screen.
final class KubeUIGuilds {
	record Member(UUID playerId, String role) {
	}

	static final class Guild {
		final String id;
		String name;
		final Map<UUID, String> members = new LinkedHashMap<>();
		final Map<String, String> relations = new ConcurrentHashMap<>();
		long xp;
		int badgeColor = 0xFFFFFFFF;
		String badgeIcon = "";

		Guild(String id, String name) {
			this.id = id;
			this.name = name;
		}
	}

	/// Lowest-to-highest rank order - `"owner"` can do anything including disbanding/promoting to
	/// owner (there's only ever one holding whichever role sits last); `"officer"` can invite/kick/
	/// promote by default; `"member"` has no management permission by default. Real defaults, not a
	/// fixed hierarchy: both the role list itself ([#setRoles]) and which role each action requires
	/// ([#setActionRequirement]) are script-settable, so a pack can add roles (a "co-owner" between
	/// officer and owner, say) or change who's allowed to do what, entirely from JS - "vraiment tout
	/// en JS" (real ask) - without needing [KubeUIPermissions]'s own general integration point,
	/// which a script that wants even finer control can still layer on top regardless.
	private static volatile List<String> ROLE_RANK = List.of("member", "officer", "owner");
	private static volatile int MAX_LEVEL = 20;
	private static volatile long XP_PER_LEVEL = 500;
	private static final Map<String, String> ACTION_REQUIREMENTS = new ConcurrentHashMap<>(Map.of(
		"invite", "officer",
		"kick", "officer",
		"setRole", "owner"
	));

	private static final Map<String, Guild> GUILDS = new ConcurrentHashMap<>();
	private static MinecraftServer serverRef;
	private static volatile boolean dirty;

	private KubeUIGuilds() {
	}

	/// Redefines the role hierarchy, lowest-to-highest (e.g. `["member", "officer", "co-owner",
	/// "owner"]`) - existing members keep whatever role string they already have (even one no
	/// longer in the new list, which simply can't act on anything requiring a rank until reassigned
	/// - not retroactively bumped/demoted). Ignored (a no-op) if `roles` is null/empty.
	static void setRoles(List<String> roles) {
		if (roles != null && !roles.isEmpty()) {
			ROLE_RANK = List.copyOf(roles);
		}
	}

	static List<String> roles() {
		return ROLE_RANK;
	}

	/// `action` is `"invite"`/`"kick"`/`"setRole"` - `minRole` must already be one of [#ROLE_RANK].
	static void setActionRequirement(String action, String minRole) {
		if (action != null && minRole != null && ROLE_RANK.contains(minRole)) {
			ACTION_REQUIREMENTS.put(action, minRole);
		}
	}

	static void setLevelCurve(int maxLevel, long xpPerLevel) {
		if (maxLevel > 0 && xpPerLevel > 0) {
			MAX_LEVEL = maxLevel;
			XP_PER_LEVEL = xpPerLevel;
		}
	}

	private static String topRole() {
		return ROLE_RANK.get(ROLE_RANK.size() - 1);
	}

	static boolean create(ServerPlayer founder, String guildId, String name) {
		if (GUILDS.containsKey(guildId) || guildOf(founder) != null) {
			return false;
		}
		var guild = new Guild(guildId, name);
		guild.members.put(founder.getUUID(), topRole());
		GUILDS.put(guildId, guild);
		dirty = true;
		return true;
	}

	static Guild get(String guildId) {
		return GUILDS.get(guildId);
	}

	/// Only the owner can rename their own guild ("donner un nom à notre guild via des commandes" -
	/// the guild's display name isn't fixed at creation, unlike its `guildId`).
	static boolean rename(ServerPlayer actor, String guildId, String newName) {
		var guild = GUILDS.get(guildId);
		if (guild == null || newName == null || newName.isBlank() || !hasPermission(actor, guildId, topRole())) {
			return false;
		}
		guild.name = newName;
		dirty = true;
		return true;
	}

	static int count() {
		return GUILDS.size();
	}

	static Guild guildOf(ServerPlayer player) {
		for (var guild : GUILDS.values()) {
			if (guild.members.containsKey(player.getUUID())) {
				return guild;
			}
		}
		return null;
	}

	private static boolean outranks(String role, String required) {
		return ROLE_RANK.indexOf(role) >= ROLE_RANK.indexOf(required);
	}

	static boolean hasPermission(ServerPlayer player, String guildId, String requiredRole) {
		var guild = GUILDS.get(guildId);
		var role = guild != null ? guild.members.get(player.getUUID()) : null;
		return role != null && outranks(role, requiredRole);
	}

	static boolean invite(ServerPlayer inviter, String guildId, ServerPlayer target) {
		var guild = GUILDS.get(guildId);
		if (guild == null || !hasPermission(inviter, guildId, ACTION_REQUIREMENTS.get("invite")) || guild.members.containsKey(target.getUUID())) {
			return false;
		}
		guild.members.put(target.getUUID(), ROLE_RANK.get(0));
		dirty = true;
		return true;
	}

	static boolean kick(ServerPlayer actor, String guildId, UUID targetId) {
		var guild = GUILDS.get(guildId);
		if (guild == null || !hasPermission(actor, guildId, ACTION_REQUIREMENTS.get("kick")) || topRole().equals(guild.members.get(targetId))) {
			return false;
		}
		dirty |= guild.members.remove(targetId) != null;
		return true;
	}

	static boolean setRole(ServerPlayer actor, String guildId, UUID targetId, String role) {
		var guild = GUILDS.get(guildId);
		if (guild == null || !hasPermission(actor, guildId, ACTION_REQUIREMENTS.get("setRole")) || !ROLE_RANK.contains(role) || topRole().equals(role)) {
			return false;
		}
		if (!guild.members.containsKey(targetId)) {
			return false;
		}
		guild.members.put(targetId, role);
		dirty = true;
		return true;
	}

	static void sendChat(ServerPlayer sender, String message) {
		var guild = guildOf(sender);
		if (guild == null || serverRef == null) {
			return;
		}
		var line = net.minecraft.network.chat.Component.literal("§d[" + guild.name + "] §f" + sender.getGameProfile().getName() + ": §7" + message);
		for (var member : guild.members.keySet()) {
			var online = serverRef.getPlayerList().getPlayer(member);
			if (online != null) {
				online.sendSystemMessage(line);
			}
		}
	}

	static void setRelation(String guildA, String guildB, String relation) {
		if (!GUILDS.containsKey(guildA) || !GUILDS.containsKey(guildB)) {
			return;
		}
		GUILDS.get(guildA).relations.put(guildB, relation);
		GUILDS.get(guildB).relations.put(guildA, relation);
		dirty = true;
	}

	static String relation(String guildA, String guildB) {
		var guild = GUILDS.get(guildA);
		return guild != null ? guild.relations.getOrDefault(guildB, "neutral") : "neutral";
	}

	static void addXp(String guildId, long amount) {
		var guild = GUILDS.get(guildId);
		if (guild != null && amount > 0) {
			guild.xp += amount;
			dirty = true;
		}
	}

	static int level(String guildId) {
		var guild = GUILDS.get(guildId);
		return guild == null ? 0 : (int) Math.min(MAX_LEVEL, guild.xp / XP_PER_LEVEL);
	}

	static void setBadge(String guildId, int color, String icon) {
		var guild = GUILDS.get(guildId);
		if (guild != null) {
			guild.badgeColor = color;
			guild.badgeIcon = icon == null ? "" : icon;
			dirty = true;
		}
	}

	static List<Guild> byXpDescending() {
		return GUILDS.values().stream().sorted(Comparator.comparingLong((Guild g) -> g.xp).reversed()).toList();
	}

	// ---------------------------------------------------------------- persistence

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("kubeui_guilds.dat");
	}

	static void load(MinecraftServer server) {
		serverRef = server;
		GUILDS.clear();
		dirty = false;

		var path = file(server);
		if (!Files.exists(path)) {
			return;
		}
		try {
			var root = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			for (var entry : KubeUINbtCompat.getListOrEmpty(root, "guilds")) {
				if (!(entry instanceof CompoundTag tag)) {
					continue;
				}
				var guild = new Guild(KubeUINbtCompat.getStringOr(tag, "id", ""), KubeUINbtCompat.getStringOr(tag, "name", ""));
				guild.xp = KubeUINbtCompat.getLongOr(tag, "xp", 0);
				guild.badgeColor = KubeUINbtCompat.getIntOr(tag, "badgeColor", 0xFFFFFFFF);
				guild.badgeIcon = KubeUINbtCompat.getStringOr(tag, "badgeIcon", "");
				for (var memberTag : KubeUINbtCompat.getListOrEmpty(tag, "members")) {
					if (memberTag instanceof CompoundTag m) {
						guild.members.put(UUID.fromString(KubeUINbtCompat.getStringOr(m, "id", "")), KubeUINbtCompat.getStringOr(m, "role", "member"));
					}
				}
				for (var relationTag : KubeUINbtCompat.getListOrEmpty(tag, "relations")) {
					if (relationTag instanceof CompoundTag r) {
						guild.relations.put(KubeUINbtCompat.getStringOr(r, "guild", ""), KubeUINbtCompat.getStringOr(r, "relation", "neutral"));
					}
				}
				GUILDS.put(guild.id, guild);
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load guilds from disk", ex);
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
		var guildsTag = new ListTag();
		for (var guild : GUILDS.values()) {
			var tag = new CompoundTag();
			tag.putString("id", guild.id);
			tag.putString("name", guild.name);
			tag.putLong("xp", guild.xp);
			tag.putInt("badgeColor", guild.badgeColor);
			tag.putString("badgeIcon", guild.badgeIcon);

			var membersTag = new ListTag();
			for (var member : guild.members.entrySet()) {
				var m = new CompoundTag();
				m.putString("id", member.getKey().toString());
				m.putString("role", member.getValue());
				membersTag.add(m);
			}
			tag.put("members", membersTag);

			var relationsTag = new ListTag();
			for (var relation : guild.relations.entrySet()) {
				var r = new CompoundTag();
				r.putString("guild", relation.getKey());
				r.putString("relation", relation.getValue());
				relationsTag.add(r);
			}
			tag.put("relations", relationsTag);

			guildsTag.add(tag);
		}
		root.put("guilds", guildsTag);

		try {
			var path = file(serverRef);
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save guilds to disk", ex);
		}
	}
}
