package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Named personal spawn points beyond the vanilla single bed (`KubeUIActions.setHome(...)`/
/// `.teleportHome(...)`) - potentially several per player, stored directly on the player's own real
/// persistent data (`KubeUIActions#playerData`, same real mechanism [KubeUIFriends] already reuses)
/// rather than a dedicated file.
final class KubeUIHomes {
	private static final String KEY = "homes";

	private KubeUIHomes() {
	}

	static void set(ServerPlayer player, String name) {
		var data = KubeUIActions.playerData(player);
		var homes = new ArrayList<>(entries(data));
		homes.removeIf(tag -> tag.getStringOr("name", "").equals(name));

		var tag = new CompoundTag();
		tag.putString("name", name);
		tag.putDouble("x", player.getX());
		tag.putDouble("y", player.getY());
		tag.putDouble("z", player.getZ());
		tag.putString("dimension", player.level().dimension().identifier().toString());
		homes.add(tag);

		var listTag = new ListTag();
		listTag.addAll(homes);
		data.put(KEY, listTag);
	}

	static boolean remove(ServerPlayer player, String name) {
		var data = KubeUIActions.playerData(player);
		var homes = new ArrayList<>(entries(data));
		boolean removed = homes.removeIf(tag -> tag.getStringOr("name", "").equals(name));
		if (removed) {
			var listTag = new ListTag();
			listTag.addAll(homes);
			data.put(KEY, listTag);
		}
		return removed;
	}

	static List<String> names(ServerPlayer player) {
		return entries(KubeUIActions.playerData(player)).stream().map(tag -> tag.getStringOr("name", "")).toList();
	}

	/// Teleports `player` to `name` if it exists - returns `false` (no-op) otherwise, including if
	/// its dimension no longer exists (a datapack/modpack change removed it).
	static boolean teleportTo(ServerPlayer player, String name) {
		var home = entries(KubeUIActions.playerData(player)).stream()
			.filter(tag -> tag.getStringOr("name", "").equals(name))
			.findFirst().orElse(null);
		if (home == null) {
			return false;
		}

		var dimensionId = net.minecraft.resources.Identifier.tryParse(home.getStringOr("dimension", ""));
		if (dimensionId == null) {
			return false;
		}
		var server = player.level().getServer();
		ServerLevel level = server != null ? server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimensionId)) : null;
		if (level == null) {
			return false;
		}

		player.teleportTo(level, home.getDoubleOr("x", 0), home.getDoubleOr("y", 0), home.getDoubleOr("z", 0), Set.of(), player.getYRot(), player.getXRot(), true);
		return true;
	}

	private static List<CompoundTag> entries(CompoundTag data) {
		var result = new ArrayList<CompoundTag>();
		for (var tag : data.getListOrEmpty(KEY)) {
			if (tag instanceof CompoundTag compound) {
				result.add(compound);
			}
		}
		return result;
	}
}
