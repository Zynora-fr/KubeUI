package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// A temporary, session-only party/group (`KubeUIActions.createParty(...)`) - deliberately not
/// persisted at all (unlike [KubeUIGuilds]'s own disk-backed membership), the honest difference
/// between "a permanent faction" and "a group for tonight's dungeon run" the roadmap itself draws.
/// `sharedXp`/`sharedLoot` are just flags a script reads back (`KubeUIActions.partySharesXp(...)`) -
/// this class doesn't itself redistribute XP or loot, that stays the script's own decision at the
/// real moment XP/loot would be granted (the same "real system stays real, KubeUI just tracks
/// membership" split [KubeUIGuilds]'s territory/leveling already keep).
final class KubeUIParties {
	static final class Party {
		final UUID leader;
		final Set<UUID> members = new LinkedHashSet<>();
		boolean sharedXp;
		boolean sharedLoot;

		Party(UUID leader) {
			this.leader = leader;
			members.add(leader);
		}
	}

	private static final Map<UUID, Party> PARTIES_BY_MEMBER = new ConcurrentHashMap<>();
	/// Real default (only the leader can invite) - script-settable ([#setAnyoneCanInvite]) rather
	/// than fixed, "vraiment tout en JS" (real ask).
	private static volatile boolean anyoneCanInvite = false;
	private static volatile int maxSize = Integer.MAX_VALUE;

	private KubeUIParties() {
	}

	static void setAnyoneCanInvite(boolean value) {
		anyoneCanInvite = value;
	}

	/// Real default (unlimited) - `Integer.MAX_VALUE` or below effectively disables the cap again.
	static void setMaxSize(int value) {
		maxSize = value > 0 ? value : Integer.MAX_VALUE;
	}

	static boolean create(ServerPlayer leader) {
		if (PARTIES_BY_MEMBER.containsKey(leader.getUUID())) {
			return false;
		}
		var party = new Party(leader.getUUID());
		PARTIES_BY_MEMBER.put(leader.getUUID(), party);
		return true;
	}

	static Party partyOf(ServerPlayer player) {
		return PARTIES_BY_MEMBER.get(player.getUUID());
	}

	static boolean invite(ServerPlayer inviter, ServerPlayer target) {
		var party = partyOf(inviter);
		if (party == null || !(anyoneCanInvite || party.leader.equals(inviter.getUUID()))) {
			return false;
		}
		if (PARTIES_BY_MEMBER.containsKey(target.getUUID()) || party.members.size() >= maxSize) {
			return false;
		}
		party.members.add(target.getUUID());
		PARTIES_BY_MEMBER.put(target.getUUID(), party);
		return true;
	}

	static void leave(ServerPlayer player) {
		var party = PARTIES_BY_MEMBER.remove(player.getUUID());
		if (party == null) {
			return;
		}
		party.members.remove(player.getUUID());
		if (party.leader.equals(player.getUUID()) || party.members.isEmpty()) {
			for (var memberId : party.members) {
				PARTIES_BY_MEMBER.remove(memberId);
			}
		}
	}

	static void setSharing(ServerPlayer player, boolean sharedXp, boolean sharedLoot) {
		var party = partyOf(player);
		if (party != null && party.leader.equals(player.getUUID())) {
			party.sharedXp = sharedXp;
			party.sharedLoot = sharedLoot;
		}
	}
}
