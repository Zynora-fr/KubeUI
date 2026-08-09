package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/// Group loot need/greed voting (`KubeUIActions.startLootVote(itemId, count, participants)`) - the
/// actual voting *screen* is left to a script (`KubeUIActions.openRemote`/`KubeUIRemoteScreens`,
/// same generic real mechanism every other custom server-pushed screen in this mod already uses -
/// see `kubeui_dungeon_demo.js`), this class only owns the real, server-authoritative tally and
/// resolution rule: any real `"need"` vote beats every `"greed"` vote (same real convention a
/// classic dungeon loot-roll uses), ties broken randomly within whichever group actually won,
/// nobody wins if everyone passed.
final class KubeUILootVote {
	private static final Map<String, Vote> VOTES = new ConcurrentHashMap<>();

	private static final class Vote {
		final String itemId;
		final int count;
		final List<UUID> participants;
		final Map<UUID, String> choices = new ConcurrentHashMap<>();

		Vote(String itemId, int count, List<UUID> participants) {
			this.itemId = itemId;
			this.count = count;
			this.participants = participants;
		}
	}

	private KubeUILootVote() {
	}

	static String start(String itemId, int count, List<ServerPlayer> participants) {
		String voteId = UUID.randomUUID().toString();
		var ids = participants.stream().map(ServerPlayer::getUUID).toList();
		VOTES.put(voteId, new Vote(itemId, count, ids));
		return voteId;
	}

	/// Records `player`'s choice (`"need"`/`"greed"`/`"pass"`) - once every participant has voted,
	/// resolves and gives the item to the winner (dropped at their feet if their inventory is full,
	/// same fallback every other item-granting path in this mod already uses), returning the
	/// winner's name (or `""` if nobody won/the vote isn't resolved yet).
	static String castVote(String voteId, ServerPlayer player, String choice) {
		var vote = VOTES.get(voteId);
		if (vote == null || !vote.participants.contains(player.getUUID())) {
			return "";
		}
		vote.choices.put(player.getUUID(), choice);
		if (vote.choices.size() < vote.participants.size()) {
			return "";
		}

		VOTES.remove(voteId);
		var needVoters = vote.choices.entrySet().stream().filter(e -> "need".equals(e.getValue())).map(Map.Entry::getKey).toList();
		var greedVoters = vote.choices.entrySet().stream().filter(e -> "greed".equals(e.getValue())).map(Map.Entry::getKey).toList();
		var pool = !needVoters.isEmpty() ? needVoters : greedVoters;
		if (pool.isEmpty()) {
			return "";
		}

		var winnerId = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
		var winner = player.level().getServer().getPlayerList().getPlayer(winnerId);
		if (winner == null) {
			return "";
		}
		var item = KubeUICurrency.resolveItem(vote.itemId);
		if (item != null) {
			KubeUICurrency.giveItem(winner, item, Math.max(1, vote.count));
		}
		return winner.getGameProfile().name();
	}
}
