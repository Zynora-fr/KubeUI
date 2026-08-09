package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/// Private per-player notes on other players (`KubeUI.setContactNote(uuid, name, note)`) -
/// "purement côté client, jamais partagé" (real ask: purely client-side, never shared), same real
/// local compressed-NBT persistence [KubeUIWaypoints]/[KubeUIBlockList] already established. Never
/// sent to the server or any other client - a note is only ever readable by whoever wrote it.
public final class KubeUIContactNotes {
	public record Note(UUID playerId, String playerName, String note) {
	}

	private static final Map<UUID, Note> NOTES = new LinkedHashMap<>();
	private static boolean loaded = false;

	private KubeUIContactNotes() {
	}

	private static Path file() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("kubeui").resolve("contact_notes.dat");
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
			for (var tag : root.getListOrEmpty("notes")) {
				if (tag instanceof CompoundTag noteTag) {
					try {
						var id = UUID.fromString(noteTag.getStringOr("id", ""));
						NOTES.put(id, new Note(id, noteTag.getStringOr("name", ""), noteTag.getStringOr("note", "")));
					} catch (IllegalArgumentException ignored) {
					}
				}
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load contact notes from disk", ex);
		}
	}

	private static void save() {
		var root = new CompoundTag();
		var listTag = new ListTag();
		for (var note : NOTES.values()) {
			var tag = new CompoundTag();
			tag.putString("id", note.playerId().toString());
			tag.putString("name", note.playerName());
			tag.putString("note", note.note());
			listTag.add(tag);
		}
		root.put("notes", listTag);

		try {
			var path = file();
			Files.createDirectories(path.getParent());
			NbtIo.writeCompressed(root, path);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save contact notes to disk", ex);
		}
	}

	public static void set(UUID playerId, String playerName, String note) {
		ensureLoaded();
		if (note == null || note.isBlank()) {
			NOTES.remove(playerId);
		} else {
			NOTES.put(playerId, new Note(playerId, playerName, note));
		}
		save();
	}

	public static Note get(UUID playerId) {
		ensureLoaded();
		return NOTES.get(playerId);
	}

	public static java.util.Collection<Note> all() {
		ensureLoaded();
		return NOTES.values();
	}
}
