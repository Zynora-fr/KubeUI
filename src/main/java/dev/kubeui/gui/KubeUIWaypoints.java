package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// Client-persisted waypoints - "sauvegardés côté client" is this session's real,
/// working option (the roadmap's own "ou serveur si partagés" alternative is handled differently:
/// Sharing shares one specific waypoint to one specific player over the network rather than
/// hosting the whole list server-side - see [KubeUIActions#WAYPOINT_SHARE_ACTION]). Saved under
/// `<game dir>/kubeui/waypoints.dat`, real compressed NBT (`NbtIo`), the same mechanism
/// [KubeUIQuests]'s editor-quest file uses - global to the Minecraft install, not per-world, an
/// honest simplification: per-world waypoint files would need knowing the current world/server's
/// identity reliably across both singleplayer and multiplayer, which isn't simple.
public final class KubeUIWaypoints {
	private static final List<KubeUIWaypoint> WAYPOINTS = new ArrayList<>();
	private static String trackedId = null;
	private static boolean loaded = false;

	private KubeUIWaypoints() {
	}

	private static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("kubeui").resolve("waypoints.dat");
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;

		var path = file();
		if (!Files.exists(path)) {
			return;
		}

		try {
			var root = NbtIo.readCompressed(path, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			for (var entry : KubeUINbtCompat.getListOrEmpty(root, "waypoints")) {
				if (entry instanceof CompoundTag tag) {
					WAYPOINTS.add(new KubeUIWaypoint(
						KubeUINbtCompat.getStringOr(tag, "id", UUID.randomUUID().toString()),
						KubeUINbtCompat.getStringOr(tag, "name", "Waypoint"),
						KubeUINbtCompat.getDoubleOr(tag, "x", 0), KubeUINbtCompat.getDoubleOr(tag, "y", 0), KubeUINbtCompat.getDoubleOr(tag, "z", 0),
						KubeUINbtCompat.getStringOr(tag, "dimension", "minecraft:overworld"),
						KubeUINbtCompat.getIntOr(tag, "color", 0xFFFFFFFF)
					));
				}
			}
			String trackedString = KubeUINbtCompat.getStringOr(root, "tracked", "");
			trackedId = trackedString.isEmpty() ? null : trackedString;
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load waypoints from disk", ex);
		}
	}

	private static void save() {
		var root = new CompoundTag();
		var listTag = new ListTag();
		for (var waypoint : WAYPOINTS) {
			var tag = new CompoundTag();
			tag.putString("id", waypoint.id());
			tag.putString("name", waypoint.name());
			tag.putDouble("x", waypoint.x());
			tag.putDouble("y", waypoint.y());
			tag.putDouble("z", waypoint.z());
			tag.putString("dimension", waypoint.dimension());
			tag.putInt("color", waypoint.color());
			listTag.add(tag);
		}
		root.put("waypoints", listTag);
		if (trackedId != null) {
			root.putString("tracked", trackedId);
		}

		try {
			var path = file();
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save waypoints to disk", ex);
		}
	}

	public static List<KubeUIWaypoint> all() {
		ensureLoaded();
		return List.copyOf(WAYPOINTS);
	}

	public static KubeUIWaypoint add(String name, double x, double y, double z, String dimension, int color) {
		ensureLoaded();
		var waypoint = new KubeUIWaypoint(UUID.randomUUID().toString(), name, x, y, z, dimension, color);
		WAYPOINTS.add(waypoint);
		save();
		return waypoint;
	}

	/// Used by [KubeUIActions#WAYPOINT_SHARE_ACTION]'s reply handler - a shared waypoint already
	/// has its own real id from whoever created it, kept as-is rather than minted fresh, so
	/// accepting the same share twice doesn't create a duplicate.
	public static void addShared(KubeUIWaypoint waypoint) {
		ensureLoaded();
		if (WAYPOINTS.stream().noneMatch(w -> w.id().equals(waypoint.id()))) {
			WAYPOINTS.add(waypoint);
			save();
		}
	}

	public static void remove(String id) {
		ensureLoaded();
		if (WAYPOINTS.removeIf(w -> w.id().equals(id))) {
			if (id.equals(trackedId)) {
				trackedId = null;
			}
			save();
		}
	}

	public static void setTracked(String id) {
		ensureLoaded();
		trackedId = id;
		save();
	}

	public static KubeUIWaypoint tracked() {
		ensureLoaded();
		if (trackedId == null) {
			return null;
		}
		return WAYPOINTS.stream().filter(w -> w.id().equals(trackedId)).findFirst().orElse(null);
	}
}
