package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Named, script-defined score boards (`KubeUIActions.setLeaderboardScore(...)`) - the general
/// "communiquer en JS avec les DB" leaderboard access point asked for, alongside the already-real
/// specific ones ([KubeUIGuilds#byXpDescending], [KubeUIDungeons]'s own completion times): a script
/// picks any `boardId` it likes (a minigame's kill count, a farm's total harvest, anything) and
/// this handles storage/sorting/persistence for it, the same real disk-persisted `<world>/data/`
/// mechanism [KubeUIGuilds]/[KubeUIClaims] already established, rather than a script rolling its
/// own NBT file every time it wants a new leaderboard.
final class KubeUILeaderboards {
	record Entry(UUID playerId, String playerName, double score) {
	}

	/// `boardId -> (playerId -> Entry)` - `Entry` keeps its own `playerName` snapshot (last-seen,
	/// not live-resolved) so [#top] still shows a name for a player who's currently offline.
	private static final Map<String, Map<UUID, Entry>> BOARDS = new ConcurrentHashMap<>();
	private static MinecraftServer serverRef;
	private static volatile boolean dirty;

	private KubeUILeaderboards() {
	}

	static void setScore(String boardId, UUID playerId, String playerName, double score) {
		BOARDS.computeIfAbsent(boardId, ignored -> new ConcurrentHashMap<>())
			.put(playerId, new Entry(playerId, playerName, score));
		dirty = true;
	}

	static double scoreOf(String boardId, UUID playerId) {
		var board = BOARDS.get(boardId);
		var entry = board != null ? board.get(playerId) : null;
		return entry != null ? entry.score() : 0;
	}

	static boolean removeScore(String boardId, UUID playerId) {
		var board = BOARDS.get(boardId);
		if (board == null) {
			return false;
		}
		dirty |= board.remove(playerId) != null;
		return true;
	}

	static void clearBoard(String boardId) {
		dirty |= BOARDS.remove(boardId) != null;
	}

	/// Highest score first, at most `limit` entries.
	static List<Entry> top(String boardId, int limit) {
		var board = BOARDS.get(boardId);
		if (board == null) {
			return List.of();
		}
		return board.values().stream()
			.sorted(Comparator.comparingDouble(Entry::score).reversed())
			.limit(Math.max(0, limit))
			.toList();
	}

	static List<String> boardIds() {
		return List.copyOf(BOARDS.keySet());
	}

	// ---------------------------------------------------------------- persistence

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data").resolve("kubeui_leaderboards.dat");
	}

	static void load(MinecraftServer server) {
		serverRef = server;
		BOARDS.clear();
		dirty = false;

		var path = file(server);
		if (!Files.exists(path)) {
			return;
		}
		try {
			var root = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			for (var boardTag : KubeUINbtCompat.getListOrEmpty(root, "boards")) {
				if (!(boardTag instanceof CompoundTag tag)) {
					continue;
				}
				String boardId = KubeUINbtCompat.getStringOr(tag, "id", "");
				var entries = new LinkedHashMap<UUID, Entry>();
				for (var entryTag : KubeUINbtCompat.getListOrEmpty(tag, "entries")) {
					if (entryTag instanceof CompoundTag e) {
						var playerId = UUID.fromString(KubeUINbtCompat.getStringOr(e, "playerId", ""));
						entries.put(playerId, new Entry(playerId, KubeUINbtCompat.getStringOr(e, "playerName", ""), KubeUINbtCompat.getDoubleOr(e, "score", 0)));
					}
				}
				BOARDS.put(boardId, new ConcurrentHashMap<>(entries));
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load leaderboards from disk", ex);
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
		var boardsTag = new ListTag();
		for (var board : BOARDS.entrySet()) {
			var tag = new CompoundTag();
			tag.putString("id", board.getKey());

			var entriesTag = new ListTag();
			for (var entry : board.getValue().values()) {
				var e = new CompoundTag();
				e.putString("playerId", entry.playerId().toString());
				e.putString("playerName", entry.playerName());
				e.putDouble("score", entry.score());
				entriesTag.add(e);
			}
			tag.put("entries", entriesTag);

			boardsTag.add(tag);
		}
		root.put("boards", boardsTag);

		try {
			var path = file(serverRef);
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save leaderboards to disk", ex);
		}
	}
}
