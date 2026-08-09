package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Script-defined dungeons (`KubeUIActions.defineDungeon(id, name, roomIds)`) - rooms/chests/boss
/// tracked per player (not per group - see the class doc note below), an ordered room list doubling
/// as the progression lock (`#isRoomUnlocked`: room N only unlocks once room N-1 has actually been
/// visited), and a completion-time leaderboard fed automatically the moment
/// [#markBossDefeated] is called after every room's been visited.
///
/// Per-player rather than per-group progress is the honest, reduced scope here: a real "group"
/// concept (party membership, shared state across several players) doesn't exist anywhere else in
/// this codebase yet either, and inventing one just for dungeons would be a second, disconnected
/// mechanism rather than a real shared primitive - a script that wants "the whole group shares
/// progress" can already build that itself by calling the same per-player methods for every member
/// of its own party list.
final class KubeUIDungeons {
	record Def(String id, String name, List<String> roomIds) {
	}

	record LeaderboardEntry(UUID playerId, String playerName, long timeMs) {
	}

	private static final Map<String, Def> DEFS = new ConcurrentHashMap<>();
	/// (dungeonId, playerUuid) -> visited room ids, in visit order
	private static final Map<String, Map<UUID, Set<String>>> VISITED_ROOMS = new ConcurrentHashMap<>();
	private static final Map<String, Map<UUID, Set<String>>> OPENED_CHESTS = new ConcurrentHashMap<>();
	private static final Map<String, Map<UUID, Long>> START_TIME_MS = new ConcurrentHashMap<>();
	private static final Map<String, Map<UUID, Boolean>> BOSS_DEFEATED = new ConcurrentHashMap<>();
	private static final Map<String, List<LeaderboardEntry>> LEADERBOARDS = new ConcurrentHashMap<>();

	private KubeUIDungeons() {
	}

	static int count() {
		return DEFS.size();
	}

	static void define(String id, String name, List<String> roomIds) {
		DEFS.put(id, new Def(id, name, List.copyOf(roomIds)));
	}

	static Def def(String id) {
		return DEFS.get(id);
	}

	static boolean isRoomUnlocked(String dungeonId, ServerPlayer player, String roomId) {
		var def = DEFS.get(dungeonId);
		if (def == null) {
			return false;
		}
		int index = def.roomIds().indexOf(roomId);
		if (index <= 0) {
			return index == 0;
		}
		var visited = visitedRooms(dungeonId, player);
		return visited.contains(def.roomIds().get(index - 1));
	}

	static boolean markRoomVisited(String dungeonId, ServerPlayer player, String roomId) {
		if (!isRoomUnlocked(dungeonId, player, roomId)) {
			return false;
		}
		var visited = visitedRoomsMutable(dungeonId, player);
		if (visited.isEmpty()) {
			START_TIME_MS.computeIfAbsent(dungeonId, ignored -> new ConcurrentHashMap<>()).put(player.getUUID(), System.currentTimeMillis());
		}
		visited.add(roomId);
		return true;
	}

	static void markChestOpened(String dungeonId, ServerPlayer player, String chestId) {
		OPENED_CHESTS.computeIfAbsent(dungeonId, ignored -> new ConcurrentHashMap<>())
			.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashSet<>())
			.add(chestId);
	}

	/// Records a completion (all rooms visited, boss down) - fails cleanly (returns `false`, no
	/// leaderboard entry) if any room is still unvisited, so a script can't accidentally credit a
	/// speedrun for a dungeon the player skipped through.
	static boolean markBossDefeated(String dungeonId, ServerPlayer player) {
		var def = DEFS.get(dungeonId);
		if (def == null || !visitedRooms(dungeonId, player).containsAll(def.roomIds())) {
			return false;
		}
		BOSS_DEFEATED.computeIfAbsent(dungeonId, ignored -> new ConcurrentHashMap<>()).put(player.getUUID(), true);

		var startedAt = START_TIME_MS.getOrDefault(dungeonId, Map.of()).get(player.getUUID());
		long timeMs = startedAt != null ? System.currentTimeMillis() - startedAt : 0;
		var leaderboard = LEADERBOARDS.computeIfAbsent(dungeonId, ignored -> new ArrayList<>());
		synchronized (leaderboard) {
			leaderboard.add(new LeaderboardEntry(player.getUUID(), player.getGameProfile().getName(), timeMs));
			leaderboard.sort(Comparator.comparingLong(LeaderboardEntry::timeMs));
		}
		return true;
	}

	static CompoundTag progress(String dungeonId, ServerPlayer player) {
		var def = DEFS.get(dungeonId);
		var data = new CompoundTag();
		if (def == null) {
			return data;
		}
		data.putString("id", def.id());
		data.putString("name", def.name());

		var visited = visitedRooms(dungeonId, player);
		var roomsTag = new ListTag();
		for (var roomId : def.roomIds()) {
			var tag = new CompoundTag();
			tag.putString("id", roomId);
			tag.putBoolean("visited", visited.contains(roomId));
			tag.putBoolean("unlocked", isRoomUnlocked(dungeonId, player, roomId));
			roomsTag.add(tag);
		}
		data.put("rooms", roomsTag);
		data.putInt("chestsOpened", OPENED_CHESTS.getOrDefault(dungeonId, Map.of()).getOrDefault(player.getUUID(), Set.of()).size());
		data.putBoolean("bossDefeated", BOSS_DEFEATED.getOrDefault(dungeonId, Map.of()).getOrDefault(player.getUUID(), false));
		return data;
	}

	static List<LeaderboardEntry> leaderboard(String dungeonId, int limit) {
		var leaderboard = LEADERBOARDS.getOrDefault(dungeonId, List.of());
		synchronized (leaderboard) {
			return leaderboard.stream().limit(limit).toList();
		}
	}

	/// Clears every tracked player's progress for `dungeonId` - the caller (a script, gated behind
	/// its own `KubeUI.confirm()`) is responsible for the "avertissement du groupe concerné" warning,
	/// the same way [KubeUIDungeons] leaves *what* counts as a group entirely up to the script.
	static void reset(String dungeonId) {
		VISITED_ROOMS.remove(dungeonId);
		OPENED_CHESTS.remove(dungeonId);
		START_TIME_MS.remove(dungeonId);
		BOSS_DEFEATED.remove(dungeonId);
	}

	private static Set<String> visitedRooms(String dungeonId, ServerPlayer player) {
		return VISITED_ROOMS.getOrDefault(dungeonId, Map.of()).getOrDefault(player.getUUID(), Set.of());
	}

	private static Set<String> visitedRoomsMutable(String dungeonId, ServerPlayer player) {
		return VISITED_ROOMS.computeIfAbsent(dungeonId, ignored -> new ConcurrentHashMap<>())
			.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashSet<>());
	}
}
