package dev.kubeui.gui;

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// A persistent per-player friends list (`KubeUIActions.addFriend(...)`/`.removeFriend(...)`/
/// `.friendsList(player)`) - independent of guild membership, stored server-side directly on the
/// player's own real persistent data (`KubeUIActions#playerData`, the exact same mechanism the
/// networking demo's "remember my color" already proved works across reconnects/restarts) rather
/// than a second, separate registry file - one real friend list per player is small enough that a
/// dedicated ledger (like [KubeUIGuilds]'s own) would just be unnecessary bookkeeping.
final class KubeUIFriends {
	private static final String KEY = "friends";

	private KubeUIFriends() {
	}

	static boolean add(ServerPlayer player, UUID friendId) {
		var data = KubeUIActions.playerDataRaw(player);
		var friends = new ArrayList<>(ids(data));
		if (friends.contains(friendId)) {
			return false;
		}
		friends.add(friendId);
		writeIds(data, friends);
		return true;
	}

	static boolean remove(ServerPlayer player, UUID friendId) {
		var data = KubeUIActions.playerDataRaw(player);
		var friends = new ArrayList<>(ids(data));
		if (!friends.remove(friendId)) {
			return false;
		}
		writeIds(data, friends);
		return true;
	}

	static boolean areFriends(ServerPlayer player, UUID otherId) {
		return ids(KubeUIActions.playerDataRaw(player)).contains(otherId);
	}

	static List<UUID> friendsOf(ServerPlayer player) {
		return ids(KubeUIActions.playerDataRaw(player));
	}

	private static List<UUID> ids(net.minecraft.nbt.CompoundTag data) {
		var result = new ArrayList<UUID>();
		for (var tag : KubeUINbtCompat.getListOrEmpty(data, KEY)) {
			if (tag instanceof StringTag stringTag) {
				try {
					result.add(UUID.fromString(stringTag.getAsString()));
				} catch (IllegalArgumentException ignored) {
					// A corrupt/foreign entry shouldn't take the whole list down with it.
				}
			}
		}
		return result;
	}

	private static void writeIds(net.minecraft.nbt.CompoundTag data, List<UUID> ids) {
		var listTag = new ListTag();
		for (var id : ids) {
			listTag.add(StringTag.valueOf(id.toString()));
		}
		data.put(KEY, listTag);
	}
}
