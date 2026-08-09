package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/// Backs `KubeUIActions.defineShop(...)`/`KubeUI.shop(shopId)` - a named vendor with a
/// fixed list of [KubeUIShopStock] lines, each independently buyable and (optionally) sellable.
/// Re-declared every server boot from a script, the same "in-memory registry, no file
/// persistence" convention [KubeUIVillagerTrades]'s trade pools already use - a shop's *catalog*
/// isn't player state, only the currency balances it moves through [KubeUICurrency] are.
final class KubeUIShop {
	private static final Random RANDOM = new Random();
	private static final Map<String, String> DISPLAY_NAMES = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, KubeUIShopStock>> SHOPS = new ConcurrentHashMap<>();

	private KubeUIShop() {
	}

	static void define(String shopId, String displayName, List<KubeUIShopStock> stock) {
		DISPLAY_NAMES.put(shopId, displayName);
		var byId = new LinkedHashMap<String, KubeUIShopStock>();
		for (var entry : stock) {
			byId.put(entry.id, entry);
		}
		SHOPS.put(shopId, byId);
	}

	static boolean exists(String shopId) {
		return SHOPS.containsKey(shopId);
	}

	/// A snapshot for the client to render - never the live [KubeUIShopStock] objects themselves,
	/// so a screen already open can't observe a price update mid-frame in a half-consistent way.
	static CompoundTag describe(String shopId) {
		var tag = new CompoundTag();
		tag.putString("id", shopId);
		tag.putString("name", DISPLAY_NAMES.getOrDefault(shopId, shopId));

		var stockTag = new ListTag();
		for (var entry : SHOPS.getOrDefault(shopId, Map.of()).values()) {
			var entryTag = new CompoundTag();
			entryTag.putString("id", entry.id);
			entryTag.putString("item", entry.itemId);
			entryTag.putInt("count", entry.itemCount);
			entryTag.putString("currency", entry.currency);
			entryTag.putLong("price", entry.currentPrice);
			entryTag.putBoolean("sellable", entry.sellable);
			entryTag.putInt("stock", entry.stock);
			stockTag.add(entryTag);
		}
		tag.put("stock", stockTag);
		return tag;
	}

	/// Atomic: charges `qty * currentPrice` (fails cleanly, nothing mutated, if the player can't
	/// afford it or `qty` exceeds remaining stock), then gives `qty * itemCount` of the item.
	/// Stock of `-1` (set via `stock: -1` in the script definition) never decrements - an
	/// intentionally infinite line, not a bug in the counter.
	static boolean buy(ServerPlayer player, String shopId, String stockId, int qty) {
		var entry = SHOPS.getOrDefault(shopId, Map.of()).get(stockId);
		if (entry == null || qty <= 0) {
			return false;
		}
		if (entry.stock >= 0 && entry.stock < qty) {
			return false;
		}

		var item = KubeUICurrency.resolveItem(entry.itemId);
		if (item == null) {
			return false;
		}

		if (!KubeUICurrency.charge(player, entry.currency, entry.currentPrice * qty)) {
			return false;
		}

		KubeUICurrency.giveItem(player, item, entry.itemCount * qty);
		if (entry.stock >= 0) {
			entry.stock -= qty;
		}
		return true;
	}

	/// The reverse of [#buy] - fails cleanly if the line isn't `sellable`, or the player doesn't
	/// actually have `qty * itemCount` of the item. Sold items are consumed (not added back to
	/// [KubeUIShopStock#stock]) - a shop's buy-stock and its willingness to purchase from players
	/// are deliberately independent concepts here, not two views of one counter.
	static boolean sell(ServerPlayer player, String shopId, String stockId, int qty) {
		var entry = SHOPS.getOrDefault(shopId, Map.of()).get(stockId);
		if (entry == null || !entry.sellable || qty <= 0) {
			return false;
		}

		var item = KubeUICurrency.resolveItem(entry.itemId);
		if (item == null) {
			return false;
		}

		int needed = entry.itemCount * qty;
		if (KubeUICurrency.countItem(player, item) < needed) {
			return false;
		}

		long payout = Math.round(entry.currentPrice * entry.sellRate) * qty;
		KubeUICurrency.removeItem(player, item, needed);
		KubeUICurrency.pay(player, entry.currency, Math.max(1, payout));
		return true;
	}

	/// Called once per [KubeUICurrencyEvents]-owned interval check (see there for the shared tick
	/// gating) - every fluctuating line across every shop takes one bounded random step, clamped to
	/// its own `[minPrice, maxPrice]`. `tickCount` lets each line fluctuate on its own configured
	/// `fluctuationIntervalTicks` independently rather than forcing one global interval for every
	/// shop.
	static void tickFluctuation(long tickCount) {
		for (var shop : SHOPS.values()) {
			for (var entry : shop.values()) {
				if (!entry.fluctuates() || tickCount % entry.fluctuationIntervalTicks != 0) {
					continue;
				}
				long range = entry.maxPrice - entry.minPrice;
				long step = Math.max(1, range / 20);
				long delta = (RANDOM.nextInt(3) - 1) * step;
				entry.currentPrice = clamp(entry.currentPrice + delta, entry.minPrice, entry.maxPrice);
			}
		}
	}

	private static long clamp(long value, long min, long max) {
		return Math.max(min, Math.min(max, value));
	}
}
