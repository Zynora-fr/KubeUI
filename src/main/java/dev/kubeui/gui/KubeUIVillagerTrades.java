package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/// Real, server-authoritative custom trading - deliberately built entirely on top of primitives
/// this project already has proven working (`Entity#getPersistentData()`, the same real vanilla
/// inventory manipulation `kubeui_shop_real.js` already uses, weighted-random selection in plain
/// Java) rather than integrating with vanilla's own `VillagerTrades`/`MerchantOffer` system.
///
/// That system turned out, on decompiling this exact Minecraft version, to have been completely
/// overhauled since `VillagerTrades.ItemListing` was removed entirely: `MerchantOffer` now takes
/// `ItemCost`/`TradeCost` records, `VillagerProfession` references `TradeSet`/`VillagerTrade`
/// **dynamic datapack registry** entries rather than plain Java arrays, and whether KubeJS's
/// generic `ServerEvents.registry('villager_trade'/'trade_set', ...)` hook actually fires for
/// those two registries could only be confirmed by bytecode inference (`DataPackRegistriesHooks
/// .getDataPackRegistries()` inclusion), not a live smoke test this environment can run (no world
/// join possible here). Building on an unconfirmed mechanism risked shipping something that
/// silently doesn't work - this design avoids that risk entirely while still delivering real
/// weighted trades, stock/restock, and condition-gating.
///
/// A trade isn't tied to a *new* entity type either - [KubeUIVillagerTradeInteraction] triggers on
/// **any** entity tagged with a pool id via [#tagPool], vanilla villager or not (confirmed: KubeJS
/// has no entity-type registration hook at all in this version, so a genuinely new custom entity
/// class would have to be real, unassisted Java - reusing an existing entity type as the "not
/// necessarily a villager" merchant sidesteps that gap rather than attempting it).
final class KubeUIVillagerTrades {
	private static final String POOL_TAG_KEY = "kubeui_trade_pool";
	private static final String INLINE_TRADES_TAG_KEY = "kubeui_inline_trades";
	private static final String ROLLED_TAG_KEY = "kubeui_trade_rolled";
	private static final String STATE_TAG_KEY = "kubeui_trade_state";
	private static final String HISTORY_TAG_KEY = "kubeui_trade_history";
	private static final int MAX_OFFERS_PER_ENTITY = 5;

	private static final Map<String, List<KubeUITradeDef>> POOLS = new ConcurrentHashMap<>();
	private static final Map<String, KubeUITradeCondition> CONDITIONS = new ConcurrentHashMap<>();
	private static final Random RANDOM = new Random();

	private KubeUIVillagerTrades() {
	}

	static void registerPool(String poolId, List<KubeUITradeDef> trades, KubeUITradeCondition condition) {
		POOLS.put(poolId, trades);
		if (condition != null) {
			CONDITIONS.put(poolId, condition);
		} else {
			CONDITIONS.remove(poolId);
		}
	}

	static void tagPool(Entity entity, String poolId) {
		entity.getPersistentData().putString(POOL_TAG_KEY, poolId);
	}

	static String poolOf(Entity entity) {
		return KubeUINbtCompat.getStringOr(entity.getPersistentData(), POOL_TAG_KEY, "");
	}

	/// Bakes `trades` directly onto `entity`'s own persistent data - real vanilla entity NBT,
	/// saved/loaded with the entity like any other tag, unlike [#registerPool]'s `POOLS` map (a
	/// plain in-memory `Map`, wiped on every server restart). [KubeUITraderEggItem] uses this
	/// instead of `#registerPool`/`#tagPool` for exactly that reason: a real, reported bug where a
	/// trader spawned from an egg lost its trades after closing and reopening the game - the entity
	/// itself survived the save/reload fine (real vanilla entity persistence), but the pool it was
	/// pointing to by id had evaporated along with the server process that registered it. A trade
	/// pool registered by a script (`KubeUIActions.registerTradePool`/`.tagTradePool`) doesn't have
	/// this problem in the first place - the script re-registers it under the same id every time the
	/// server starts, so the `POOLS` map is always repopulated before anything could ask for it -
	/// this only affects trades created at runtime with no script backing them, i.e. exactly the
	/// egg case.
	static void tagInlineTrades(Entity entity, List<KubeUITradeDef> trades) {
		var listTag = new ListTag();
		for (var def : trades) {
			listTag.add(tradeToTag(def));
		}
		entity.getPersistentData().put(INLINE_TRADES_TAG_KEY, listTag);
	}

	private static List<KubeUITradeDef> inlineTradesOf(Entity entity) {
		var root = entity.getPersistentData();
		if (!root.contains(INLINE_TRADES_TAG_KEY)) {
			return null;
		}

		var result = new ArrayList<KubeUITradeDef>();
		for (var tag : root.getList(INLINE_TRADES_TAG_KEY, 10)) {
			if (tag instanceof CompoundTag compoundTag) {
				result.add(tradeFromTag(compoundTag));
			}
		}
		return result;
	}

	/// Which trades `entity` actually offers, regardless of which of the two storage mechanisms
	/// above it came from - inline trades (baked directly onto the entity, see
	/// [#tagInlineTrades]) take priority if present, otherwise falls back to the named `POOLS`
	/// entry for [#poolOf]. Everything else in this class (offer rolling, stock, matching) reads
	/// through this rather than caring which mechanism is in play.
	private static List<KubeUITradeDef> poolFor(Entity entity) {
		var inline = inlineTradesOf(entity);
		if (inline != null) {
			return inline;
		}
		return POOLS.getOrDefault(poolOf(entity), List.of());
	}

	static boolean hasPool(Entity entity) {
		return entity.getPersistentData().contains(INLINE_TRADES_TAG_KEY) || (!poolOf(entity).isEmpty() && POOLS.containsKey(poolOf(entity)));
	}

	/// Backs `/kubeui villager-trades <target>` - inspects an entity's currently-rolled trades
	/// (and each one's uses-remaining) without needing to read its persistent-data NBT by hand.
	static String describe(Entity entity, ServerPlayer viewer) {
		if (!hasPool(entity)) {
			return entity.getName().getString() + " isn't tagged with a trade pool.";
		}

		var offers = currentOffers(entity, viewer);
		String poolId = poolOf(entity);
		String label = poolId.isEmpty() ? "inline trades" : "pool '" + poolId + "'";
		if (offers.isEmpty()) {
			return entity.getName().getString() + " (" + label + ") currently has no eligible trades for " + viewer.getName().getString() + ".";
		}

		var sb = new StringBuilder(entity.getName().getString() + " (" + label + "):\n");
		for (var def : offers) {
			sb.append("  ").append(def.id()).append(": ").append(usesRemaining(entity, def)).append('/').append(def.maxUses()).append(" uses, ")
				.append(def.resultCount()).append('x').append(def.resultItem()).append('\n');
		}
		return sb.toString();
	}

	/// The trades this specific entity currently offers `player` - rolled once per entity (cached
	/// on it via persistent data, not re-rolled every interaction) and filtered by
	/// [KubeUITradeCondition] if the pool has one (never applies to inline trades - a script-only
	/// concept). Uses-remaining/restock is resolved separately per trade via [#usesRemaining] - a
	/// trade can still appear here at 0 uses, the screen is expected to show that rather than hide
	/// it (so a player can see what "sold out" rather than wonder if it ever existed).
	static List<KubeUITradeDef> currentOffers(Entity entity, ServerPlayer player) {
		String poolId = poolOf(entity);
		var pool = poolFor(entity);
		if (pool.isEmpty()) {
			return List.of();
		}

		var root = entity.getPersistentData();
		List<String> rolledIds = new ArrayList<>();
		if (root.contains(ROLLED_TAG_KEY)) {
			for (var tag : root.getList(ROLLED_TAG_KEY, 8)) {
				rolledIds.add(tag.getAsString());
			}
		} else {
			rolledIds = rollWeighted(pool, Math.min(pool.size(), MAX_OFFERS_PER_ENTITY));
			var listTag = new ListTag();
			rolledIds.forEach(id -> listTag.add(StringTag.valueOf(id)));
			root.put(ROLLED_TAG_KEY, listTag);
		}

		var condition = CONDITIONS.get(poolId);
		var result = new ArrayList<KubeUITradeDef>();
		for (String id : rolledIds) {
			for (var def : pool) {
				if (def.id().equals(id) && (condition == null || condition.test(player, id))) {
					result.add(def);
					break;
				}
			}
		}
		return result;
	}

	private static List<String> rollWeighted(List<KubeUITradeDef> pool, int count) {
		var remaining = new ArrayList<>(pool);
		var picked = new ArrayList<String>();

		for (int i = 0; i < count && !remaining.isEmpty(); i++) {
			int totalWeight = 0;
			for (var def : remaining) {
				totalWeight += Math.max(1, def.weight());
			}

			int roll = RANDOM.nextInt(totalWeight);
			int cumulative = 0;
			KubeUITradeDef chosen = remaining.get(remaining.size() - 1);
			for (var def : remaining) {
				cumulative += Math.max(1, def.weight());
				if (roll < cumulative) {
					chosen = def;
					break;
				}
			}

			picked.add(chosen.id());
			remaining.remove(chosen);
		}

		return picked;
	}

	private static CompoundTag tradeState(Entity entity) {
		var root = entity.getPersistentData();
		return KubeUINbtCompat.getCompoundOrCreate(root, STATE_TAG_KEY);
	}

	static int usesRemaining(Entity entity, KubeUITradeDef def) {
		var state = tradeState(entity);
		var tradeTag = KubeUINbtCompat.getCompoundOrNull(state, def.id());
		if (tradeTag == null) {
			return def.maxUses();
		}

		long now = entity.level().getGameTime();
		long restockAt = KubeUINbtCompat.getLongOr(tradeTag, "restockAt", -1);
		if (restockAt >= 0 && now >= restockAt) {
			return def.maxUses();
		}

		return KubeUINbtCompat.getIntOr(tradeTag, "uses", def.maxUses());
	}

	private static void consumeUse(Entity entity, KubeUITradeDef def) {
		var state = tradeState(entity);
		int remaining = usesRemaining(entity, def) - 1;

		var tradeTag = new CompoundTag();
		tradeTag.putInt("uses", remaining);
		if (remaining <= 0 && def.restockTicks() > 0) {
			tradeTag.putLong("restockAt", entity.level().getGameTime() + def.restockTicks());
		}
		state.put(def.id(), tradeTag);
	}

	/// Used by [KubeUITradeExecuteMenu] (the real merchant-style trading GUI, real interactive
	/// payment slots instead of a trusted client claim) - which of `entity`'s current, in-stock
	/// offers the items physically sitting in its two payment slots pay for, or `null` if they
	/// don't match any of them. `cost2` empty is fine (matches a single-cost offer); a stack bigger
	/// than the required count is fine too (real vanilla merchant behavior - only the needed amount
	/// gets consumed, see [KubeUITradeExecuteMenu]).
	static KubeUITradeDef findMatchingOffer(Entity entity, ServerPlayer player, ItemStack cost1, ItemStack cost2) {
		if (cost1.isEmpty()) {
			return null;
		}
		for (var def : currentOffers(entity, player)) {
			if (usesRemaining(entity, def) > 0 && matchesCosts(def, cost1, cost2)) {
				return def;
			}
		}
		return null;
	}

	private static boolean matchesCosts(KubeUITradeDef def, ItemStack cost1, ItemStack cost2) {
		var costs = def.costs();
		if (costs.isEmpty() || costs.size() > 2) {
			return false;
		}

		var firstItem = resolveItem(costs.get(0).item());
		if (firstItem == null || !cost1.is(firstItem) || cost1.getCount() < costs.get(0).count()) {
			return false;
		}

		if (costs.size() == 1) {
			return cost2.isEmpty();
		}

		var secondItem = resolveItem(costs.get(1).item());
		return secondItem != null && cost2.is(secondItem) && cost2.getCount() >= costs.get(1).count();
	}

	/// Called once [KubeUITradeExecuteMenu] has already consumed the payment slots and handed the
	/// result to the player - just the stock/history bookkeeping, the menu (not this class) owns
	/// the actual item movement.
	static void recordCompletedTrade(Entity entity, ServerPlayer player, KubeUITradeDef def) {
		consumeUse(entity, def);
		recordHistory(player, def.id());
	}

	private static void recordHistory(ServerPlayer player, String tradeId) {
		var root = player.getPersistentData();
		var history = new ListTag();
		history.addAll(root.getList(HISTORY_TAG_KEY, 8));
		history.add(StringTag.valueOf(tradeId));
		root.put(HISTORY_TAG_KEY, history);
	}

	static List<String> tradeHistory(ServerPlayer player) {
		var result = new ArrayList<String>();
		for (var tag : player.getPersistentData().getList(HISTORY_TAG_KEY, 8)) {
			result.add(tag.getAsString());
		}
		return result;
	}

	private static Item resolveItem(String itemId) {
		var identifier = ResourceLocation.tryParse(itemId);
		return identifier != null ? BuiltInRegistries.ITEM.getOptional(identifier).orElse(null) : null;
	}

	/// NBT round-trip for a [KubeUITradeDef] - used to bake a trader egg's trade list onto its
	/// `CustomData` item component ([KubeUITraderEggItem]), a different persistence need from
	/// [#registerPool]'s in-memory `POOLS` map (an egg's trades live on the item itself, not in a
	/// script-registered pool, until the egg is used and [KubeUITraderEggItem] registers a
	/// fresh one-off pool for the spawned entity).
	static CompoundTag tradeToTag(KubeUITradeDef def) {
		var tag = new CompoundTag();
		tag.putString("id", def.id());
		tag.putInt("weight", def.weight());
		var costs = new ListTag();
		for (var cost : def.costs()) {
			var costTag = new CompoundTag();
			costTag.putString("item", cost.item());
			costTag.putInt("count", cost.count());
			costs.add(costTag);
		}
		tag.put("costs", costs);
		tag.putString("resultItem", def.resultItem());
		tag.putInt("resultCount", def.resultCount());
		tag.putInt("maxUses", def.maxUses());
		tag.putInt("restockTicks", def.restockTicks());
		return tag;
	}

	static KubeUITradeDef tradeFromTag(CompoundTag tag) {
		var costs = new ArrayList<KubeUITradeCost>();
		for (var entry : tag.getList("costs", 10)) {
			if (entry instanceof CompoundTag costTag) {
				costs.add(new KubeUITradeCost(KubeUINbtCompat.getStringOr(costTag, "item", ""), Math.max(1, KubeUINbtCompat.getIntOr(costTag, "count", 1))));
			}
		}
		return new KubeUITradeDef(
			KubeUINbtCompat.getStringOr(tag, "id", ""),
			Math.max(1, KubeUINbtCompat.getIntOr(tag, "weight", 1)),
			costs,
			KubeUINbtCompat.getStringOr(tag, "resultItem", ""),
			Math.max(1, KubeUINbtCompat.getIntOr(tag, "resultCount", 1)),
			Math.max(1, KubeUINbtCompat.getIntOr(tag, "maxUses", 12)),
			Math.max(0, KubeUINbtCompat.getIntOr(tag, "restockTicks", 24000))
		);
	}
}
