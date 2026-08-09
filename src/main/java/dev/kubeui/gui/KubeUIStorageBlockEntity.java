package dev.kubeui.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/// The real, in-world-persisted container behind [KubeUIStorageBlock] - extends real
/// vanilla `BaseContainerBlockEntity` (decompiled from `BarrelBlockEntity`'s own use of it) rather
/// than reimplementing `Container`/`MenuProvider`/NBT save-load from scratch, gaining real
/// `LockCode`-adjacent plumbing for free - though [#authorizedPlayers] is this class's
/// *own* list, not vanilla's item-key-based `LockCode`, since the roadmap explicitly asked for more
/// than that (a real per-player allow-list, not "holds a specific key item").
///
/// Bundles four of this batch's phases directly onto the one real container, rather than four
/// separate classes: [#sortBy] (302), [#filterMode]/[#filterItems]/[#canPlaceItem] (303),
/// [#authorizedPlayers]/[#canOpen] (308), [#history] (307, capped at [#MAX_HISTORY]) - all genuinely
/// exercise the *same* underlying `items`, so splitting them into separate objects would only add
/// indirection, not real separation of concerns.
public final class KubeUIStorageBlockEntity extends BaseContainerBlockEntity {
	static final int SLOT_COUNT = 27;
	private static final int MAX_HISTORY = 50;

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
	private String filterMode = "none"; // "none" | "whitelist" | "blacklist"
	private List<String> filterItems = new ArrayList<>();
	private UUID owner;
	private boolean locked;
	private List<UUID> authorizedPlayers = new ArrayList<>();
	private final List<HistoryEntry> history = new ArrayList<>();
	private String networkId = "";

	record HistoryEntry(String type, String item, int count, String playerName, long time) {
	}

	public KubeUIStorageBlockEntity(BlockPos pos, BlockState state) {
		super(KubeUIBlockEntities.STORAGE.get(), pos, state);
	}

	// ---------------------------------------------------------------- BaseContainerBlockEntity hooks

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.kubeui.storage_crate");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	/// Reuses real vanilla `ChestMenu` directly (its public 5-arg constructor accepts an arbitrary
	/// `Container` - decompiled and confirmed, not assumed) rather than a hand-written menu class -
	/// slot layout/click/shift-click/quick-move behavior is then guaranteed identical to a real
	/// chest's, for free. Only the *screen* is custom (see [KubeUIStorageScreen]), for the sort/
	/// search extras - the menu itself needs nothing KubeUI-specific.
	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new net.minecraft.world.inventory.ChestMenu(KubeUIMenus.STORAGE.get(), containerId, inventory, this, 3);
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return matchesFilter(stack);
	}

	// ---------------------------------------------------------------- locking

	public void setOwnerIfAbsent(Player player) {
		if (owner == null) {
			owner = player.getUUID();
			authorizedPlayers.add(owner);
			setChanged();
		}
	}

	public boolean canOpen(Player player) {
		return !locked || player.getUUID().equals(owner) || authorizedPlayers.contains(player.getUUID());
	}

	void setLocked(boolean locked) {
		this.locked = locked;
		setChanged();
	}

	boolean isKubeLocked() {
		return locked;
	}

	void addAuthorizedPlayer(UUID playerId) {
		if (!authorizedPlayers.contains(playerId)) {
			authorizedPlayers.add(playerId);
			setChanged();
		}
	}

	void removeAuthorizedPlayer(UUID playerId) {
		if (authorizedPlayers.remove(playerId)) {
			setChanged();
		}
	}

	List<UUID> authorizedPlayers() {
		return authorizedPlayers;
	}

	UUID owner() {
		return owner;
	}

	// ---------------------------------------------------------------- filters

	void setFilter(String mode, List<String> itemIds) {
		this.filterMode = "whitelist".equals(mode) || "blacklist".equals(mode) ? mode : "none";
		this.filterItems = new ArrayList<>(itemIds);
		setChanged();
	}

	String filterMode() {
		return filterMode;
	}

	List<String> filterItems() {
		return filterItems;
	}

	private boolean matchesFilter(ItemStack stack) {
		if ("none".equals(filterMode) || filterItems.isEmpty()) {
			return true;
		}
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		boolean inList = key != null && filterItems.contains(key.toString());
		return "whitelist".equals(filterMode) == inList;
	}

	// ---------------------------------------------------------------- sorting

	/// Reorders real inventory slots in place - `"name"` (item registry id, alphabetical),
	/// `"count"` (largest stacks first), or `"category"` (grouped by the item's registry
	/// namespace, then by name within a namespace - a simple, always-real stand-in for "category"
	/// that needs no extra per-item metadata KubeUI would otherwise have to invent/maintain).
	void sortBy(String key) {
		var stacks = new ArrayList<ItemStack>();
		for (var stack : items) {
			if (!stack.isEmpty()) {
				stacks.add(stack);
			}
		}

		Comparator<ItemStack> comparator = switch (key) {
			case "count" -> Comparator.comparingInt(ItemStack::getCount).reversed();
			case "category" -> Comparator
				.comparing((ItemStack s) -> itemKey(s).getNamespace())
				.thenComparing(s -> itemKey(s).getPath());
			default -> Comparator.comparing(s -> itemKey(s).getPath());
		};
		stacks.sort(comparator);

		for (int i = 0; i < items.size(); i++) {
			items.set(i, i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
		}
		setChanged();
	}

	private static ResourceLocation itemKey(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key != null ? key : ResourceLocation.withDefaultNamespace("air");
	}

	// ---------------------------------------------------------------- movement history

	private NonNullList<ItemStack> openSnapshot;

	/// Real vanilla `Container#startOpen`/`#stopOpen` (called by `ChestMenu` itself on construction/
	/// `#removed`, decompiled and confirmed - not a KubeUI-added hook) bracket one "session" at this
	/// container. Attributing an individual click to a "deposit" or "withdraw" would need
	/// subclassing `ChestMenu` to diff every single `clicked`/`quickMoveStack` call - real, but
	/// real risk for something this session already chose to keep maximally simple by reusing
	/// vanilla `ChestMenu` unmodified (see [#createMenu]). Instead: one before/after diff per
	/// session, net item-count deltas only - correctly attributes *who* and *roughly what* changed,
	/// at the honest cost of not distinguishing "deposited then withdrew the same stack" from
	/// "never touched it" within one session.
	@Override
	public void startOpen(Player user) {
		openSnapshot = NonNullList.create();
		for (var stack : items) {
			openSnapshot.add(stack.copy());
		}
	}

	@Override
	public void stopOpen(Player user) {
		if (openSnapshot != null) {
			diffAndRecordSession(openSnapshot, user.getGameProfile().getName());
			openSnapshot = null;
		}
	}

	private void diffAndRecordSession(NonNullList<ItemStack> before, String playerName) {
		var beforeCounts = new java.util.HashMap<Item, Integer>();
		for (var stack : before) {
			if (!stack.isEmpty()) {
				beforeCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}
		var afterCounts = new java.util.HashMap<Item, Integer>();
		for (var stack : items) {
			if (!stack.isEmpty()) {
				afterCounts.merge(stack.getItem(), stack.getCount(), Integer::sum);
			}
		}

		var allItems = new java.util.HashSet<Item>();
		allItems.addAll(beforeCounts.keySet());
		allItems.addAll(afterCounts.keySet());
		for (var item : allItems) {
			int delta = afterCounts.getOrDefault(item, 0) - beforeCounts.getOrDefault(item, 0);
			if (delta > 0) {
				recordMovement("deposit", item, delta, playerName);
			} else if (delta < 0) {
				recordMovement("withdraw", item, -delta, playerName);
			}
		}
	}

	void recordMovement(String type, Item item, int count, String playerName) {
		var key = BuiltInRegistries.ITEM.getKey(item);
		history.add(0, new HistoryEntry(type, key != null ? key.toString() : "?", count, playerName, System.currentTimeMillis()));
		while (history.size() > MAX_HISTORY) {
			history.remove(history.size() - 1);
		}
		setChanged();
	}

	List<HistoryEntry> history() {
		return history;
	}

	// ---------------------------------------------------------------- network

	void setNetworkId(String id) {
		KubeUIStorageNetwork.unregister(this);
		networkId = id == null ? "" : id;
		if (!networkId.isEmpty()) {
			KubeUIStorageNetwork.register(networkId, this);
		}
		setChanged();
	}

	String networkId() {
		return networkId;
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (!networkId.isEmpty()) {
			KubeUIStorageNetwork.register(networkId, this);
		}
	}

	/// Real vanilla `Block`/`BlockBehaviour` has no generic "drop a container's contents on
	/// removal" hook left in this version (`ChestBlock`'s own loot table, decompiled and checked,
	/// only copies `custom_name` - contents aren't dropped through the loot system at all, and no
	/// `onRemove`-named method exists anywhere in this version's `Block`/`BlockBehaviour` to search
	/// for a call to `Containers#dropContents` in). Rather than guess further at an undocumented
	/// mechanism, this drops contents explicitly and directly - the one thing genuinely guaranteed
	/// to run exactly once, with real access to `level`/`worldPosition`/`items`, when this block
	/// entity is actually being removed (real vanilla `Container#stillValid`/`setRemoved` lifecycle,
	/// not a KubeUI-invented hook).
	@Override
	public void setRemoved() {
		if (level != null && !level.isClientSide()) {
			net.minecraft.world.Containers.dropContents(level, worldPosition, items);
		}
		super.setRemoved();
		KubeUIStorageNetwork.unregister(this);
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void saveAdditional(net.minecraft.nbt.CompoundTag output, net.minecraft.core.HolderLookup.Provider registries) {
		super.saveAdditional(output, registries);
		ContainerHelper.saveAllItems(output, items, registries);

		output.putString("filterMode", filterMode);
		output.put("filterItems", stringListTag(filterItems));
		output.putBoolean("locked", locked);
		if (owner != null) {
			output.putString("owner", owner.toString());
		}
		output.put("authorizedPlayers", stringListTag(authorizedPlayers.stream().map(UUID::toString).toList()));
		output.putString("networkId", networkId);

		var historyList = new net.minecraft.nbt.ListTag();
		for (var entry : history) {
			var child = new net.minecraft.nbt.CompoundTag();
			child.putString("type", entry.type());
			child.putString("item", entry.item());
			child.putInt("count", entry.count());
			child.putString("playerName", entry.playerName());
			child.putLong("time", entry.time());
			historyList.add(child);
		}
		output.put("history", historyList);
	}

	private static net.minecraft.nbt.ListTag stringListTag(List<String> values) {
		var listTag = new net.minecraft.nbt.ListTag();
		for (var value : values) {
			listTag.add(net.minecraft.nbt.StringTag.valueOf(value));
		}
		return listTag;
	}

	@Override
	protected void loadAdditional(net.minecraft.nbt.CompoundTag input, net.minecraft.core.HolderLookup.Provider registries) {
		super.loadAdditional(input, registries);
		items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items, registries);

		filterMode = KubeUINbtCompat.getStringOr(input, "filterMode", "none");
		filterItems = new ArrayList<>();
		for (var tag : KubeUINbtCompat.getListOrEmpty(input, "filterItems")) {
			if (tag instanceof net.minecraft.nbt.StringTag s) {
				filterItems.add(s.getAsString());
			}
		}
		locked = KubeUINbtCompat.getBooleanOr(input, "locked", false);
		String ownerString = KubeUINbtCompat.getStringOr(input, "owner", "");
		owner = ownerString.isEmpty() ? null : UUID.fromString(ownerString);
		authorizedPlayers = new ArrayList<>();
		for (var tag : KubeUINbtCompat.getListOrEmpty(input, "authorizedPlayers")) {
			if (tag instanceof net.minecraft.nbt.StringTag s) {
				authorizedPlayers.add(UUID.fromString(s.getAsString()));
			}
		}
		networkId = KubeUINbtCompat.getStringOr(input, "networkId", "");

		history.clear();
		for (var tag : KubeUINbtCompat.getListOrEmpty(input, "history")) {
			if (tag instanceof net.minecraft.nbt.CompoundTag child) {
				history.add(new HistoryEntry(
					KubeUINbtCompat.getStringOr(child, "type", ""),
					KubeUINbtCompat.getStringOr(child, "item", ""),
					KubeUINbtCompat.getIntOr(child, "count", 0),
					KubeUINbtCompat.getStringOr(child, "playerName", "?"),
					KubeUINbtCompat.getLongOr(child, "time", 0)
				));
			}
		}
	}
}
