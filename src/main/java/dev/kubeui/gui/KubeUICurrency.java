package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Backs `KubeUIActions.pay`/`.charge`/`.transferCurrency`/`.exchange*`/currency history &amp;
/// leaderboard queries. Same overall shape as
/// [KubeUIQuests] (in-memory registry plus a real disk-persisted file under `&lt;world&gt;/data/`) with
/// one deliberate difference: [KubeUIQuests] saves on every *definition* change (rare - a script
/// reload, an editor save), but a balance can change many times per second on a busy shop server,
/// so writing a compressed NBT file synchronously on every single [#pay]/[#charge] would be real,
/// avoidable lag. Mutations here only flip [#dirty]; the actual disk write is periodic (see
/// [KubeUICurrencyEvents]) plus once more on server shutdown, so at most a few seconds of the very
/// latest transactions could theoretically be lost to a hard crash - an explicit, reasoned trade
/// against unconditional per-call I/O, not an oversight.
///
/// Balances/history are keyed by `UUID` rather than [ServerPlayer] so a leaderboard/admin query
/// works for currently-offline players too, without needing `PlayerDataStorage` (the API that can
/// read an *offline* player's own persistent data, and which would have made this simpler to keep
/// on `Entity#getPersistentData()` the way [KubeUIActions#playerData] does - but only for the
/// player asking, never every player at once, which a leaderboard fundamentally needs).
final class KubeUICurrency {
	private static final int MAX_HISTORY_PER_PLAYER = 200;

	private static final Set<String> KNOWN_CURRENCIES = ConcurrentHashMap.newKeySet();
	private static final Map<String, Map<UUID, Long>> BALANCES = new ConcurrentHashMap<>();
	private static final Map<UUID, String> LAST_KNOWN_NAME = new ConcurrentHashMap<>();
	private static final Map<UUID, List<CompoundTag>> HISTORY = new ConcurrentHashMap<>();
	private static final Map<String, Double> TRANSFER_TAX_RATE = new ConcurrentHashMap<>();

	private static MinecraftServer serverRef;
	private static volatile boolean dirty;

	private KubeUICurrency() {
	}

	// ---------------------------------------------------------------- currencies & balances

	static void registerCurrency(String currency) {
		KNOWN_CURRENCIES.add(currency);
	}

	static Set<String> knownCurrencies() {
		return Set.copyOf(KNOWN_CURRENCIES);
	}

	static long balance(UUID playerId, String currency) {
		var forCurrency = BALANCES.get(currency);
		return forCurrency == null ? 0L : forCurrency.getOrDefault(playerId, 0L);
	}

	static long balance(ServerPlayer player, String currency) {
		return balance(player.getUUID(), currency);
	}

	private static void setBalance(UUID playerId, String currency, long amount) {
		BALANCES.computeIfAbsent(currency, ignored -> new ConcurrentHashMap<>()).put(playerId, Math.max(0, amount));
	}

	/// Credits `player` - fails only on a non-positive `amount` (never a "silent negative
	/// balance", the explicit design goal from the roadmap entry, though credit can't
	/// actually go negative in the first place - [#charge] is where that matters).
	static boolean pay(ServerPlayer player, String currency, long amount) {
		if (amount <= 0) {
			return false;
		}
		registerCurrency(currency);
		long newBalance = balance(player, currency) + amount;
		setBalance(player.getUUID(), currency, newBalance);
		rememberName(player);
		recordTransaction(player.getUUID(), currency, amount, newBalance, "pay");
		KubeUIEconomyEvents.postTransaction(player, currency, amount, newBalance, "pay");
		dirty = true;
		return true;
	}

	/// Debits `player` - fails cleanly (no mutation, no negative balance) if their balance is
	/// below `amount`, or if `amount` isn't positive.
	static boolean charge(ServerPlayer player, String currency, long amount) {
		if (amount <= 0) {
			return false;
		}
		long current = balance(player, currency);
		if (current < amount) {
			return false;
		}
		setBalance(player.getUUID(), currency, current - amount);
		rememberName(player);
		recordTransaction(player.getUUID(), currency, -amount, current - amount, "charge");
		KubeUIEconomyEvents.postTransaction(player, currency, -amount, current - amount, "charge");
		dirty = true;
		return true;
	}

	/// Atomic player-to-player transfer: `from` is charged the full `amount` (fails the same way
	/// [#charge] would if they can't afford it - `to` is never touched in that case); `to` receives
	/// `amount` minus whatever [#setTransferTaxRate] configured for `currency` (rounded to the
	/// nearest whole unit - a currency here is always a whole-number ledger, never fractional). The
	/// tax itself isn't credited anywhere - a deliberate sink, matching a real commission (the
	/// server keeps it), not a transfer to a third party nothing here defines.
	static boolean transfer(ServerPlayer from, ServerPlayer to, String currency, long amount) {
		if (amount <= 0 || from.getUUID().equals(to.getUUID())) {
			return false;
		}
		if (!charge(from, currency, amount)) {
			return false;
		}
		double taxRate = TRANSFER_TAX_RATE.getOrDefault(currency, 0.0);
		long tax = Math.round(amount * Math.max(0, Math.min(1, taxRate)));
		pay(to, currency, Math.max(1, amount - tax));
		return true;
	}

	static void setTransferTaxRate(String currency, double rate) {
		TRANSFER_TAX_RATE.put(currency, Math.max(0, Math.min(1, rate)));
	}

	// ---------------------------------------------------------------- item <-> currency exchange

	/// Removes `itemCount` of `itemId` from `player`'s real inventory (scanned by predicate via
	/// `Inventory#clearOrCountMatchingItems` - the same vanilla method a crafting menu uses to
	/// consume ingredients, not a hand-rolled slot scan) and credits `currencyAmount` - fails
	/// (no mutation at all) if the player doesn't actually have `itemCount` of the item.
	static boolean exchangeItemForCurrency(ServerPlayer player, String itemId, int itemCount, String currency, long currencyAmount) {
		var item = resolveItem(itemId);
		if (item == null || itemCount <= 0) {
			return false;
		}
		if (countItem(player, item) < itemCount) {
			return false;
		}
		removeItem(player, item, itemCount);
		pay(player, currency, currencyAmount);
		return true;
	}

	/// The reverse of [#exchangeItemForCurrency] - charges first (fails cleanly, same as
	/// [#charge], if the player can't afford it), then gives the item (added to the inventory if
	/// there's room, dropped at the player's feet otherwise - the same fallback [KubeUIQuests]'s
	/// own item rewards already use, never silently discarded).
	static boolean exchangeCurrencyForItem(ServerPlayer player, String currency, long currencyAmount, String itemId, int itemCount) {
		var item = resolveItem(itemId);
		if (item == null || itemCount <= 0) {
			return false;
		}
		if (!charge(player, currency, currencyAmount)) {
			return false;
		}
		giveItem(player, item, itemCount);
		return true;
	}

	static Item resolveItem(String itemId) {
		var key = ResourceLocation.tryParse(itemId);
		return key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
	}

	static int countItem(ServerPlayer player, Item item) {
		return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), 0, new SimpleContainer(0));
	}

	static void removeItem(ServerPlayer player, Item item, int count) {
		player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), count, new SimpleContainer(0));
	}

	static void giveItem(ServerPlayer player, Item item, int count) {
		var stack = new ItemStack(item, count);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	// ---------------------------------------------------------------- transaction history

	private static void recordTransaction(UUID playerId, String currency, long delta, long newBalance, String type) {
		var tag = new CompoundTag();
		tag.putString("currency", currency);
		tag.putLong("delta", delta);
		tag.putLong("balance", newBalance);
		tag.putString("type", type);
		tag.putLong("time", System.currentTimeMillis());

		var list = HISTORY.computeIfAbsent(playerId, ignored -> new ArrayList<>());
		synchronized (list) {
			list.add(tag);
			while (list.size() > MAX_HISTORY_PER_PLAYER) {
				list.remove(0);
			}
		}
	}

	/// Most recent first - `currency` null/blank returns every currency's history interleaved,
	/// otherwise only entries for that one.
	static List<CompoundTag> history(ServerPlayer player, String currency, int limit) {
		var list = HISTORY.getOrDefault(player.getUUID(), List.of());
		var result = new ArrayList<CompoundTag>();
		synchronized (list) {
			for (int i = list.size() - 1; i >= 0 && result.size() < limit; i--) {
				var tag = list.get(i);
				if (currency == null || currency.isBlank() || currency.equals(KubeUINbtCompat.getStringOr(tag, "currency", ""))) {
					result.add(tag);
				}
			}
		}
		return result;
	}

	// ---------------------------------------------------------------- leaderboard

	/// Highest balance first, zero/never-touched balances excluded (a player who's simply never
	/// interacted with `currency` shouldn't clutter a leaderboard tied for last place).
	static ListTag topBalances(String currency, int limit) {
		var forCurrency = BALANCES.getOrDefault(currency, Map.of());
		var entries = new ArrayList<>(forCurrency.entrySet());
		entries.sort(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed());

		var result = new ListTag();
		for (var entry : entries) {
			if (result.size() >= limit) {
				break;
			}
			if (entry.getValue() <= 0) {
				continue;
			}
			var tag = new CompoundTag();
			tag.putString("name", LAST_KNOWN_NAME.getOrDefault(entry.getKey(), entry.getKey().toString()));
			tag.putLong("balance", entry.getValue());
			result.add(tag);
		}
		return result;
	}

	private static void rememberName(ServerPlayer player) {
		LAST_KNOWN_NAME.put(player.getUUID(), player.getGameProfile().getName());
	}

	// ---------------------------------------------------------------- disk persistence

	private static Path ledgerFile(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("kubeui_currency.dat");
	}

	/// Doesn't clear [#KNOWN_CURRENCIES]: on a singleplayer/integrated server, `server_scripts` top-
	/// level code (where `KubeUIActions.registerCurrency(...)` normally lives) runs *before*
	/// `ServerAboutToStartEvent` fires this - clearing it here would wipe out every currency a
	/// script had just registered, only to (at best) re-add the ones this same load happens to find
	/// on disk. The other maps are cleared safely: nothing populates them this early (balances/
	/// history only change at runtime, well after the world's fully started).
	static void load(MinecraftServer server) {
		serverRef = server;
		BALANCES.clear();
		LAST_KNOWN_NAME.clear();
		HISTORY.clear();
		dirty = false;

		var file = ledgerFile(server);
		if (!Files.exists(file)) {
			return;
		}

		try {
			var root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());

			for (var currencyEntry : KubeUINbtCompat.getListOrEmpty(root, "currencies")) {
				if (!(currencyEntry instanceof CompoundTag tag)) {
					continue;
				}
				String currency = KubeUINbtCompat.getStringOr(tag, "id", "");
				KNOWN_CURRENCIES.add(currency);
				var forCurrency = BALANCES.computeIfAbsent(currency, ignored -> new ConcurrentHashMap<>());
				for (var balanceEntry : KubeUINbtCompat.getListOrEmpty(tag, "balances")) {
					if (balanceEntry instanceof CompoundTag balanceTag) {
						forCurrency.put(UUID.fromString(KubeUINbtCompat.getStringOr(balanceTag, "uuid", "")), KubeUINbtCompat.getLongOr(balanceTag, "amount", 0));
					}
				}
			}

			for (var nameEntry : KubeUINbtCompat.getListOrEmpty(root, "names")) {
				if (nameEntry instanceof CompoundTag tag) {
					LAST_KNOWN_NAME.put(UUID.fromString(KubeUINbtCompat.getStringOr(tag, "uuid", "")), KubeUINbtCompat.getStringOr(tag, "name", ""));
				}
			}

			for (var historyEntry : KubeUINbtCompat.getListOrEmpty(root, "history")) {
				if (!(historyEntry instanceof CompoundTag tag)) {
					continue;
				}
				var entries = new ArrayList<CompoundTag>();
				for (var entryTag : KubeUINbtCompat.getListOrEmpty(tag, "entries")) {
					if (entryTag instanceof CompoundTag e) {
						entries.add(e);
					}
				}
				HISTORY.put(UUID.fromString(KubeUINbtCompat.getStringOr(tag, "uuid", "")), entries);
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load currency ledger from disk", ex);
		}
	}

	static boolean isDirty() {
		return dirty;
	}

	static void save() {
		if (serverRef == null) {
			return;
		}
		dirty = false;

		var root = new CompoundTag();

		var currenciesTag = new ListTag();
		for (var currency : KNOWN_CURRENCIES) {
			var tag = new CompoundTag();
			tag.putString("id", currency);
			var balancesTag = new ListTag();
			for (var entry : BALANCES.getOrDefault(currency, Map.of()).entrySet()) {
				var balanceTag = new CompoundTag();
				balanceTag.putString("uuid", entry.getKey().toString());
				balanceTag.putLong("amount", entry.getValue());
				balancesTag.add(balanceTag);
			}
			tag.put("balances", balancesTag);
			currenciesTag.add(tag);
		}
		root.put("currencies", currenciesTag);

		var namesTag = new ListTag();
		for (var entry : LAST_KNOWN_NAME.entrySet()) {
			var tag = new CompoundTag();
			tag.putString("uuid", entry.getKey().toString());
			tag.putString("name", entry.getValue());
			namesTag.add(tag);
		}
		root.put("names", namesTag);

		var historyTag = new ListTag();
		for (var entry : HISTORY.entrySet()) {
			var tag = new CompoundTag();
			tag.putString("uuid", entry.getKey().toString());
			var entriesTag = new ListTag();
			synchronized (entry.getValue()) {
				entriesTag.addAll(entry.getValue());
			}
			tag.put("entries", entriesTag);
			historyTag.add(tag);
		}
		root.put("history", historyTag);

		try {
			var file = ledgerFile(serverRef);
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(root, file);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save currency ledger to disk", ex);
		}
	}
}
