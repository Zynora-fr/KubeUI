package dev.kubeui.gui;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/// Scriptable weighted loot beyond real vanilla `LootTable`s (`KubeUIActions.defineLootTable(id,
/// entries)`/`.rollLoot(id, difficultyMultiplier)`) - real vanilla loot tables (see
/// [KubeUILootPreview] for reading one) have no first-class concept of "reweight every entry by a
/// caller-supplied multiplier at roll time" (that needs a real `LootItemCondition`/function baked
/// into the table's own JSON ahead of time), which is exactly the dynamic-by-difficulty behavior
/// asked for here - a plain script-side weighted table roll instead.
final class KubeUICustomLoot {
	record Entry(String itemId, int count, double weight) {
	}

	private static final Map<String, List<Entry>> TABLES = new ConcurrentHashMap<>();

	private KubeUICustomLoot() {
	}

	static void define(String tableId, List<Entry> entries) {
		TABLES.put(tableId, List.copyOf(entries));
	}

	/// `difficultyMultiplier` scales every entry whose `weight` is *below* 1.0 down further and
	/// every entry at/above 1.0 up further (`Math.pow(weight, 1 / max(0.01, multiplier))`) - a
	/// higher multiplier makes rare (low-weight) entries relatively more likely without needing the
	/// script to hand-author a second table per difficulty tier.
	static ItemStack roll(String tableId, double difficultyMultiplier) {
		var entries = TABLES.get(tableId);
		if (entries == null || entries.isEmpty()) {
			return ItemStack.EMPTY;
		}

		double factor = 1.0 / Math.max(0.01, difficultyMultiplier);
		double totalWeight = 0;
		for (var entry : entries) {
			totalWeight += Math.pow(Math.max(0.0001, entry.weight()), factor);
		}
		if (totalWeight <= 0) {
			return ItemStack.EMPTY;
		}

		double roll = ThreadLocalRandom.current().nextDouble() * totalWeight;
		double cumulative = 0;
		for (var entry : entries) {
			cumulative += Math.pow(Math.max(0.0001, entry.weight()), factor);
			if (roll <= cumulative) {
				var item = KubeUICurrency.resolveItem(entry.itemId());
				return item != null ? new ItemStack(item, Math.max(1, entry.count())) : ItemStack.EMPTY;
			}
		}
		return ItemStack.EMPTY;
	}
}
