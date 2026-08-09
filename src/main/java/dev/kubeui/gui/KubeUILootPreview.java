package dev.kubeui.gui;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/// A real loot table's *probable* contents (`KubeUI.lootTableBrowser(id)`) - read-only, "jamais un
/// aperçu qui pourrait laisser croire à une garantie de drop" (never a preview implying a guaranteed
/// drop): rather than trying to compute exact odds (vanilla's own `LootPool`/`LootPoolEntryContainer`
/// weights aren't publicly walkable in a generic way, and plenty of entries are conditional on
/// context this preview simply doesn't have - an enchantment level, a specific tool, ...), this
/// actually *rolls* the real table [#SAMPLE_COUNT] times with the real
/// [net.minecraft.server.MinecraftServer#reloadableRegistries] and tallies what came out - the
/// exact same real resolution path a chest opening for the first time uses
/// (`RandomizableContainer#unpackLootTable`, decompiled and mirrored here), just sampled instead of
/// applied to a real container. `LootContextParamSets.CHEST` is deliberately the target param set
/// for every table previewed this way (only `ORIGIN` is actually required, `THIS_ENTITY`/
/// `ATTACKING_ENTITY` merely optional) - the same broadly-compatible set a generic "open any random
/// container" already assumes, real vanilla precedent for a preview that can't know in advance
/// whether the table it's asked to sample is chest-shaped, entity-shaped, or something else.
final class KubeUILootPreview {
	private static final int SAMPLE_COUNT = 200;

	private KubeUILootPreview() {
	}

	/// itemId -> how many of the [#SAMPLE_COUNT] simulated rolls produced at least one of it (a
	/// rough, honest "how common" signal - not an exact drop chance, and said so wherever this is
	/// shown).
	static Map<String, Integer> sample(ServerPlayer player, String lootTableId) {
		var identifier = Identifier.tryParse(lootTableId);
		if (identifier == null) {
			return Map.of();
		}
		var key = ResourceKey.create(Registries.LOOT_TABLE, identifier);
		var level = player.level();
		LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(key);
		if (lootTable == LootTable.EMPTY) {
			return Map.of();
		}

		var params = new LootParams.Builder(level)
			.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(player.blockPosition()))
			.withParameter(LootContextParams.THIS_ENTITY, player)
			.withLuck(player.getLuck())
			.create(LootContextParamSets.CHEST);

		var counts = new LinkedHashMap<String, Integer>();
		for (int i = 0; i < SAMPLE_COUNT; i++) {
			for (var stack : lootTable.getRandomItems(params, level.getRandom())) {
				if (stack.isEmpty()) {
					continue;
				}
				var itemKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
				if (itemKey == null) {
					continue;
				}
				counts.merge(itemKey.toString(), 1, Integer::sum);
			}
		}
		return counts;
	}
}
