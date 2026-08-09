package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/// A player-blocked/muted list (`KubeUI.blockPlayer(uuid, name)`/`.isPlayerBlocked(uuid)`) -
/// "filtrage client" (real ask: client-side filtering), so this is purely local, same real
/// mechanism (client-persisted compressed NBT under `<game dir>/kubeui/`) [KubeUIWaypoints] already
/// established, not a server-authoritative ban/mute system. Other client-side systems in this mod
/// that show messages from other players (guild chat, party chat, an emote) are expected to check
/// [#isBlocked] themselves before displaying one - this class only owns the list.
public final class KubeUIBlockList {
	private static final Set<UUID> BLOCKED = new LinkedHashSet<>();
	private static boolean loaded = false;

	private KubeUIBlockList() {
	}

	private static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("kubeui").resolve("blocklist.dat");
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
			for (var tag : root.getListOrEmpty("blocked")) {
				if (tag instanceof StringTag stringTag) {
					try {
						BLOCKED.add(UUID.fromString(stringTag.value()));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load the block list from disk", ex);
		}
	}

	private static void save() {
		var root = new CompoundTag();
		var listTag = new ListTag();
		for (var id : BLOCKED) {
			listTag.add(StringTag.valueOf(id.toString()));
		}
		root.put("blocked", listTag);

		try {
			var path = file();
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save the block list to disk", ex);
		}
	}

	public static void block(UUID playerId) {
		ensureLoaded();
		if (BLOCKED.add(playerId)) {
			save();
		}
	}

	public static void unblock(UUID playerId) {
		ensureLoaded();
		if (BLOCKED.remove(playerId)) {
			save();
		}
	}

	public static boolean isBlocked(UUID playerId) {
		ensureLoaded();
		return BLOCKED.contains(playerId);
	}

	public static Set<UUID> all() {
		ensureLoaded();
		return Set.copyOf(BLOCKED);
	}
}
