package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/// The real trading GUI a player sees when right-clicking a [KubeUIVillagerTrades]-tagged entity -
/// the same real vanilla `MerchantMenu` coordinates/texture [KubeUITraderGridMenu] uses to *define*
/// a trade (`136,37`/`162,37`/`220,37` on `villager.png`), now used to actually *pay* one: real,
/// player-filled payment slots (not a KubeUI-drawn list with a trusted "Trade" button) - put in
/// items that match one of the entity's current offers and the result slot fills in automatically
/// (mirroring real vanilla `MerchantMenu`'s own "whichever offer the payment slots currently match"
/// behavior), left-click it to receive the result and pay the exact cost.
///
/// Deliberately doesn't reuse vanilla's actual `Merchant`/`MerchantOffer` system - see
/// [KubeUIVillagerTrades]'s class doc for why (removed/overhauled in this Minecraft version) - this
/// is real physical item interaction on top of the same server-authoritative
/// [KubeUIVillagerTrades] validation, just driven by menu slots instead of a client-claimed trade
/// id.
///
/// The trade list looks like real vanilla trading now: up to [#PREVIEW_ROWS] rows, each with real
/// cost1/cost2/result preview icons at the *exact* real `MerchantScreen` pixel offsets
/// (`x=10`/`40`/`73`, `y=19+20*row` - decompiled and verified, no adjustment needed since
/// `AbstractContainerScreen` draws a `Slot`'s item at exactly `slot.x, slot.y` with no inset) plus
/// the real trade-arrow sprite between cost2 and result (drawn by [KubeUITradeExecuteScreen], not
/// data - purely cosmetic, only needs to know a row is populated, which it can already tell from
/// the synced preview slots themselves). Left-clicking any of a row's 3 slots selects that offer:
/// gives back whatever's currently in the payment slots to the player's inventory first (real
/// vanilla behavior, `MerchantMenu#tryMoveItems`), then auto-fills the payment slots for the newly
/// selected offer from the player's own inventory (`#moveFromInventoryToPaymentSlot`, also real).
///
/// This reuses the same "intercept specific slot indices in #clicked" pattern already proven in
/// [KubeUIRecipeGridMenu]/[KubeUITraderGridMenu], rather than vanilla's own `Button` widgets +
/// `AbstractContainerMenu#clickMenuButton`/`ServerboundSelectTradePacket` machinery (real, but tied
/// to `MerchantMenu` specifically server-side, and would still have needed a separate network
/// channel to sync each offer's icons to the client - real slots sync for free through the exact
/// same mechanism the payment/result slots already use). Real fidelity gaps versus vanilla, still
/// noted rather than hidden: capped at 7 offers with no scrolling (matches vanilla's own
/// non-scrolled row count, `NUMBER_OF_OFFER_BUTTONS`), no discount-strikethrough/out-of-stock
/// ribbon decoration, no per-icon tooltip breakdown.
final class KubeUITradeExecuteMenu extends AbstractContainerMenu {
	static final int PREVIEW_ROWS = 7;
	private static final int COST1_PREVIEW_BASE = 0;
	private static final int COST2_PREVIEW_BASE = PREVIEW_ROWS;
	static final int RESULT_PREVIEW_BASE = PREVIEW_ROWS * 2;
	private static final int PREVIEW_SLOT_COUNT = PREVIEW_ROWS * 3;
	private static final int COST1_INDEX = PREVIEW_SLOT_COUNT;
	private static final int COST2_INDEX = PREVIEW_SLOT_COUNT + 1;
	private static final int RESULT_INDEX = PREVIEW_SLOT_COUNT + 2;
	private static final int TOP_SLOT_COUNT = PREVIEW_SLOT_COUNT + 3;

	private final Entity trader;
	private final Player player;
	private final List<KubeUITradeDef> offers;
	private KubeUITradeDef matchedOffer;

	static void open(ServerPlayer player, Entity trader) {
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public net.minecraft.network.chat.Component getDisplayName() {
				return trader.getName();
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new KubeUITradeExecuteMenu(containerId, inventory, trader);
			}
		});
	}

	/// Bare 2-arg constructor - only ever used for the client's own local instance, reconstructed
	/// from `ClientboundOpenScreenPacket` (see [KubeUIRecipeGridMenu]'s class doc for why this
	/// pattern is safe: only the *server's* instance, built via [#open], ever validates/executes a
	/// trade - the client copy exists purely for rendering, its preview/payment/result slots just
	/// mirror whatever the server's real slot contents sync down as).
	KubeUITradeExecuteMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, null);
	}

	private KubeUITradeExecuteMenu(int containerId, Inventory inventory, Entity trader) {
		super(KubeUIMenus.TRADE_EXECUTE.get(), containerId);
		this.trader = trader;
		this.player = inventory.player;
		this.offers = trader != null && this.player instanceof ServerPlayer serverPlayer
			? KubeUIVillagerTrades.currentOffers(trader, serverPlayer)
			: List.of();

		// A plain SimpleContainer#setChanged() is a real no-op (verified against the decompiled
		// class) - it never calls the menu back on its own. Real vanilla menus that need to react
		// to their own slot contents changing (e.g. StonecutterMenu) override setChanged() on their
		// backing container specifically to call back into slotsChanged(...) - same real pattern
		// needed here, or #slotsChanged below would simply never run for a normal player drag.
		//
		// The payment slots and the result slot are deliberately on separate containers from each
		// other (and from the preview slots below): #slotsChanged itself sets the result slot's
		// item, and if the result slot's container were the SAME one wired to call back into
		// slotsChanged, that set() would immediately retrigger slotsChanged -> set the result slot
		// again -> forever (a real infinite recursion/StackOverflowError this hit in testing) -
		// resultContainer/previewContainer stay plain, unwired SimpleContainers specifically so
		// writing to them never re-enters slotsChanged.
		var tradeContainer = new SimpleContainer(2) {
			@Override
			public void setChanged() {
				super.setChanged();
				KubeUITradeExecuteMenu.this.slotsChanged(this);
			}
		};
		var resultContainer = new SimpleContainer(1);
		var previewContainer = new SimpleContainer(PREVIEW_SLOT_COUNT);
		for (int i = 0; i < offers.size() && i < PREVIEW_ROWS; i++) {
			var costs = offers.get(i).costs();
			if (!costs.isEmpty()) {
				setPreviewItem(previewContainer, COST1_PREVIEW_BASE + i, costs.get(0).item(), costs.get(0).count());
			}
			if (costs.size() > 1) {
				setPreviewItem(previewContainer, COST2_PREVIEW_BASE + i, costs.get(1).item(), costs.get(1).count());
			}
			setPreviewItem(previewContainer, RESULT_PREVIEW_BASE + i, offers.get(i).resultItem(), offers.get(i).resultCount());
		}

		// Real MerchantScreen pixel offsets for the cost1/cost2/result icons within each offer row
		// (decompiled and verified: sellItem1X=xo+5+5, +35 for cost2, +68 for result, decorHeight
		// starting at yo+16+1+2 and +20 per row) - AbstractContainerScreen draws a Slot's item at
		// exactly slot.x, slot.y with no inset (also verified), so these need no adjustment.
		//
		// Deliberately three separate loops (all cost1 slots, then all cost2, then all result) -
		// not one loop adding a (cost1,cost2,result) triplet per row. `addSlot(...)` appends to
		// this menu's own `this.slots` list in call order, and `RESULT_PREVIEW_BASE`/
		// `COST1_PREVIEW_BASE`/`COST2_PREVIEW_BASE` (and `slotIndex % PREVIEW_ROWS` in #clicked)
		// both assume the menu's real slot indices are grouped by *column* in blocks of
		// `PREVIEW_ROWS`, matching the previewContainer's own internal indexing. A single
		// interleaved loop produced the exact opposite order (real menu slot index = `row*3 +
		// column`) - a real, reported bug: it broke both the trade-arrow-per-row lookup (checking
		// entirely the wrong slot, so it almost never lined up with an actual populated row) *and*
		// silently selected the wrong offer on most clicks in the preview list, since `clicked(...)`
		// derives which row was clicked the same (grouped-by-column) way.
		for (int i = 0; i < PREVIEW_ROWS; i++) {
			addPreviewSlot(previewContainer, COST1_PREVIEW_BASE + i, 10, 19 + i * 20);
		}
		for (int i = 0; i < PREVIEW_ROWS; i++) {
			addPreviewSlot(previewContainer, COST2_PREVIEW_BASE + i, 40, 19 + i * 20);
		}
		for (int i = 0; i < PREVIEW_ROWS; i++) {
			addPreviewSlot(previewContainer, RESULT_PREVIEW_BASE + i, 73, 19 + i * 20);
		}
		addSlot(new Slot(tradeContainer, 0, 136, 37));
		addSlot(new Slot(tradeContainer, 1, 162, 37));
		addSlot(new Slot(resultContainer, 0, 220, 37) {
			@Override
			public boolean mayPlace(ItemStack itemStack) {
				return false;
			}

			@Override
			public void onTake(Player takingPlayer, ItemStack carried) {
				super.onTake(takingPlayer, carried);
				completeTrade(takingPlayer);
			}
		});
		// x=108, not the usual x=8 - the real vanilla MerchantMenu positions the player's own
		// inventory slots there too (verified against its decompiled constructor), because the
		// left ~99px of villager.png's artwork is reserved for the trade-offer-button list; using
		// the generic default here is exactly what made the slots look shifted relative to the
		// texture's own inventory-grid artwork.
		addStandardInventorySlots(inventory, 108, 84);
		// Deliberately no auto-selected offer here anymore - the payment/result slots start empty
		// and stay that way until the player actually clicks a row themselves. Real vanilla doesn't
		// auto-pick an offer either (MerchantMenu#tryMoveItems only ever runs from a real button
		// click); the earlier version of this menu did it for visibility, but the player explicitly
		// asked to choose first instead of an item silently appearing in the payment slot on open.
	}

	private static void setPreviewItem(SimpleContainer previewContainer, int index, String itemId, int count) {
		var item = resolveItem(itemId);
		if (item != null) {
			previewContainer.setItem(index, new ItemStack(item, count));
		}
	}

	/// A preview icon is real, but never a real pickup target - `mayPlace`/`mayPickup` both `false`
	/// (defensive; [#clicked] already intercepts and returns before vanilla's default pickup logic
	/// would run), and `isFake()` `true` to match how vanilla itself marks a display-only slot
	/// (`ResultSlot`/`ChestMenuContainerSlot`, both verified this session), which routes rendering
	/// through `graphics.fakeItem(...)` - the same call vanilla's own offer-row icons use.
	private void addPreviewSlot(SimpleContainer previewContainer, int index, int x, int y) {
		addSlot(new Slot(previewContainer, index, x, y) {
			@Override
			public boolean mayPlace(ItemStack itemStack) {
				return false;
			}

			@Override
			public boolean mayPickup(Player takingPlayer) {
				return false;
			}

			@Override
			public boolean isFake() {
				return true;
			}
		});
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput type, Player clickingPlayer) {
		if (slotIndex < PREVIEW_SLOT_COUNT && type == ContainerInput.PICKUP && button == 0
			&& clickingPlayer instanceof ServerPlayer serverPlayer) {
			selectOffer(slotIndex % PREVIEW_ROWS, serverPlayer);
			return;
		}

		super.clicked(slotIndex, button, type, clickingPlayer);
	}

	/// Selects offer `index` for the payment slots to target - gives back whatever's currently sat
	/// in them to the player's inventory first (real vanilla behavior, `MerchantMenu#tryMoveItems`),
	/// then auto-fills from the player's own inventory for the newly selected offer
	/// (`#moveFromInventoryToPaymentSlot`, same real mechanism). No-ops for an out-of-range index
	/// (an empty/unused preview row, or this entity simply has fewer than [#PREVIEW_ROWS] offers).
	private void selectOffer(int index, ServerPlayer serverPlayer) {
		if (trader == null || index < 0 || index >= offers.size()) {
			return;
		}

		returnPaymentToInventory(serverPlayer);

		var costs = offers.get(index).costs();
		if (!costs.isEmpty()) {
			moveFromInventory(costs.get(0), COST1_INDEX);
			if (costs.size() > 1) {
				moveFromInventory(costs.get(1), COST2_INDEX);
			}
		}
	}

	private void returnPaymentToInventory(ServerPlayer serverPlayer) {
		for (int index : new int[]{COST1_INDEX, COST2_INDEX}) {
			var slot = getSlot(index);
			var stack = slot.getItem();
			if (!stack.isEmpty()) {
				if (!serverPlayer.getInventory().add(stack)) {
					serverPlayer.drop(stack, false);
				}
				slot.set(ItemStack.EMPTY);
			}
		}
	}

	private void moveFromInventory(KubeUITradeCost cost, int paymentSlotIndex) {
		var item = resolveItem(cost.item());
		if (item == null) {
			return;
		}

		int needed = cost.count();
		var destSlot = getSlot(paymentSlotIndex);
		for (int i = TOP_SLOT_COUNT; i < this.slots.size() && needed > 0; i++) {
			var invSlot = getSlot(i);
			var stack = invSlot.getItem();
			if (stack.isEmpty() || !stack.is(item)) {
				continue;
			}

			int take = Math.min(needed, stack.getCount());
			var current = destSlot.getItem();
			if (current.isEmpty()) {
				destSlot.set(stack.copyWithCount(take));
			} else {
				current.grow(take);
			}
			stack.shrink(take);
			needed -= take;
		}
	}

	@Override
	public void slotsChanged(Container container) {
		if (trader == null || !(player instanceof ServerPlayer serverPlayer)) {
			return;
		}

		var cost1 = getSlot(COST1_INDEX).getItem();
		var cost2 = getSlot(COST2_INDEX).getItem();
		matchedOffer = KubeUIVillagerTrades.findMatchingOffer(trader, serverPlayer, cost1, cost2);

		var resultStack = ItemStack.EMPTY;
		if (matchedOffer != null) {
			var resultItem = resolveItem(matchedOffer.resultItem());
			if (resultItem != null) {
				resultStack = new ItemStack(resultItem, matchedOffer.resultCount());
			}
		}
		getSlot(RESULT_INDEX).set(resultStack);
	}

	private void completeTrade(Player takingPlayer) {
		// Captured into a local *before* touching the slots below, and used only via this local
		// from here on - not re-read from the `matchedOffer` field. Slot#remove() on the payment
		// slots calls SimpleContainer#setChanged() (real, verified: it does so whenever the removal
		// is non-empty), which re-enters #slotsChanged and reassigns the `matchedOffer` field
		// (typically to null, since the payment slots are mid-shrink) *before* this method would
		// otherwise have read it - a real, confirmed reentrancy bug caught by tracing through the
		// exact same call chain that produced an earlier stack overflow in this same class.
		var offer = matchedOffer;
		if (offer == null || trader == null || !(takingPlayer instanceof ServerPlayer serverPlayer)) {
			return;
		}

		var costs = offer.costs();
		getSlot(COST1_INDEX).remove(costs.get(0).count());
		if (costs.size() > 1) {
			getSlot(COST2_INDEX).remove(costs.get(1).count());
		}

		KubeUIVillagerTrades.recordCompletedTrade(trader, serverPlayer, offer);
		matchedOffer = null;
	}

	@Override
	public ItemStack quickMoveStack(Player quickMovePlayer, int slotIndex) {
		if (slotIndex < PREVIEW_SLOT_COUNT) {
			return ItemStack.EMPTY;
		}

		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex == RESULT_INDEX) {
				if (!this.moveItemStackTo(stack, TOP_SLOT_COUNT, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
				slot.onQuickCraft(stack, clicked);
			} else if (slotIndex < TOP_SLOT_COUNT) {
				if (!this.moveItemStackTo(stack, TOP_SLOT_COUNT, this.slots.size(), false)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, COST1_INDEX, RESULT_INDEX, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}

			if (stack.getCount() == clicked.getCount()) {
				return ItemStack.EMPTY;
			}

			slot.onTake(quickMovePlayer, stack);
		}
		return clicked;
	}

	@Override
	public boolean stillValid(Player checkingPlayer) {
		return trader == null || trader.isAlive();
	}

	private static Item resolveItem(String itemId) {
		var key = net.minecraft.resources.Identifier.tryParse(itemId);
		return key != null ? BuiltInRegistries.ITEM.getValue(key) : null;
	}
}
