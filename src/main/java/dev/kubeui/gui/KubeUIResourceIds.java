package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/// Backs `.resourcePicker(id, kind, ...)` - lists every registered id of a given
/// kind. Only `"item"` currently has an icon preview (see `KubeUIScreen#buildResourceRow`);
/// `"sound"`/`"texture"` still list real ids, just without one.
final class KubeUIResourceIds {
	private KubeUIResourceIds() {
	}

	static List<String> forKind(String kind) {
		return switch (kind) {
			case "item" -> idsOf(BuiltInRegistries.ITEM.keySet());
			case "sound" -> idsOf(BuiltInRegistries.SOUND_EVENT.keySet());
			// No registry to enumerate for arbitrary textures (they're files, not registry
			// entries) - block/item ids are the closest stand-in for "things with a texture".
			case "texture" -> idsOf(BuiltInRegistries.BLOCK.keySet());
			default -> List.of();
		};
	}

	private static List<String> idsOf(java.util.Set<Identifier> ids) {
		var list = new ArrayList<String>(ids.size());
		for (var id : ids) {
			list.add(id.toString());
		}
		list.sort(String::compareTo);
		return list;
	}
}
