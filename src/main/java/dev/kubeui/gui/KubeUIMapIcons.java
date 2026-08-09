package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/// A scriptable map icon registry (`KubeUI.registerMapIcon(entityTypeId, color)` -
/// colors real, currently-loaded entities of that type on [KubeUIWorldMapScreen]/the minimap
/// widget) and an extension point for other mods (`KubeUIMapIcons.registerIconProvider(...)`,
/// a plain public Java interface - a third-party mod can depend on this optionally, the same
/// "call an extension point if present" shape [dev.kubeui.gui.KubeUIPermissions]'s own integration
/// point already uses, rather than KubeUI needing a hard dependency on any specific map/structure
/// mod).
public final class KubeUIMapIcons {
	public interface IconProvider {
		List<IconEntry> icons(Level level, double centerX, double centerZ, double radius);
	}

	public record IconEntry(double x, double z, int color, String label) {
	}

	private static final Map<String, Integer> ENTITY_TYPE_COLORS = new ConcurrentHashMap<>();
	private static final List<IconProvider> EXTENSION_PROVIDERS = new CopyOnWriteArrayList<>();

	private KubeUIMapIcons() {
	}

	public static void registerEntityIcon(String entityTypeId, int color) {
		ENTITY_TYPE_COLORS.put(entityTypeId, color);
	}

	public static void registerIconProvider(IconProvider provider) {
		EXTENSION_PROVIDERS.add(provider);
	}

	/// Real currently-loaded entities within `radius` blocks of the given center, colored per
	/// [#registerEntityIcon] (an unregistered entity type contributes no icon), plus whatever every
	/// registered [IconProvider] returns for the same area.
	static List<IconEntry> collect(Level level, double centerX, double centerZ, double radius) {
		var result = new ArrayList<IconEntry>();

		if (!ENTITY_TYPE_COLORS.isEmpty()) {
			var box = new AABB(centerX - radius, level.getMinBuildHeight(), centerZ - radius, centerX + radius, level.getMaxBuildHeight(), centerZ + radius);
			for (Entity entity : level.getEntitiesOfClass(Entity.class, box)) {
				var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
				Integer color = key != null ? ENTITY_TYPE_COLORS.get(key.toString()) : null;
				if (color != null) {
					result.add(new IconEntry(entity.getX(), entity.getZ(), color, entity.getName().getString()));
				}
			}
		}

		for (var provider : EXTENSION_PROVIDERS) {
			result.addAll(provider.icons(level, centerX, centerZ, radius));
		}

		return result;
	}
}
