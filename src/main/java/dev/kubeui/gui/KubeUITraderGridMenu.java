package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.UUID;

/// The physical "define one trade" GUI behind `/kubeui trader-designer`'s "Add Trade" button -
/// real, freely-interactive [Slot]s (same reasoning as [KubeUIRecipeGridMenu]: KubeJS's own
/// `ChestMenuData` can't do real item placement at all), laid out with the real vanilla
/// `MerchantMenu` coordinates (`136,37`/`162,37` for the two payment slots, `220,37` for the
/// result) so [KubeUITraderGridScreen]'s `villager.png` background lines up - but with a plain
/// [Slot] for the result instead of vanilla's `MerchantResultSlot` (computed from a matched
/// `MerchantOffer`, useless for defining a brand new trade). A left click on the result slot while
/// it holds an item captures cost1/cost2/result into one [KubeUITradeDef] (weight/max-uses/restock
/// left at sensible defaults - editable numerically isn't what a physical item grid is for) and
/// adds it to the player's in-progress trader ([KubeUITraderDesigner]), then closes.
final class KubeUITraderGridMenu extends AbstractContainerMenu {
	private static final int COST1_INDEX = 0;
	private static final int COST2_INDEX = 1;
	private static final int RESULT_INDEX = 2;
	private static final int TOP_SLOT_COUNT = 3;

	static void open(ServerPlayer player) {
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public net.minecraft.network.chat.Component getDisplayName() {
				return net.minecraft.network.chat.Component.literal("Add Trade");
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new KubeUITraderGridMenu(containerId, inventory);
			}
		});
	}

	KubeUITraderGridMenu(int containerId, Inventory inventory) {
		super(KubeUIMenus.TRADER_GRID.get(), containerId);

		var tradeContainer = new SimpleContainer(TOP_SLOT_COUNT);
		addSlot(new Slot(tradeContainer, COST1_INDEX, 136, 37));
		addSlot(new Slot(tradeContainer, COST2_INDEX, 162, 37));
		addSlot(new Slot(tradeContainer, RESULT_INDEX, 220, 37));
		// x=108, not the usual x=8 - see KubeUITradeExecuteMenu's identical fix for why (real
		// vanilla MerchantMenu, decompiled and verified, positions the player's own inventory
		// slots there for this exact texture).
		addStandardInventorySlots(inventory, 108, 84);
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput type, Player player) {
		if (slotIndex == RESULT_INDEX && type == ContainerInput.PICKUP && button == 0
			&& getSlot(RESULT_INDEX).hasItem() && player instanceof ServerPlayer serverPlayer
			&& tryCaptureAndAdd(serverPlayer)) {
			return;
		}

		super.clicked(slotIndex, button, type, player);
	}

	private boolean tryCaptureAndAdd(ServerPlayer player) {
		var resultStack = getSlot(RESULT_INDEX).getItem();
		var costs = new ArrayList<KubeUITradeCost>();
		addCostIfPresent(costs, getSlot(COST1_INDEX).getItem());
		addCostIfPresent(costs, getSlot(COST2_INDEX).getItem());

		if (costs.isEmpty()) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Put at least one cost item before taking the result."));
			return false;
		}

		String resultId = itemId(resultStack);
		var def = new KubeUITradeDef("trade_" + UUID.randomUUID(), 1, costs, resultId, resultStack.getCount(), 12, 24000);
		KubeUITraderDesigner.addTrade(player, def);
		player.closeContainer();
		// Reopens the Trader Designer screen with the new trade already showing, instead of
		// leaving the player at nothing needing to retype /kubeui trader-designer.
		KubeUINetworking.sendTraderDesignerList(player);
		return true;
	}

	private static void addCostIfPresent(ArrayList<KubeUITradeCost> costs, ItemStack stack) {
		if (!stack.isEmpty()) {
			costs.add(new KubeUITradeCost(itemId(stack), stack.getCount()));
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex < TOP_SLOT_COUNT) {
				if (!this.moveItemStackTo(stack, TOP_SLOT_COUNT, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, TOP_SLOT_COUNT, false)) {
				return ItemStack.EMPTY;
			}

			if (stack.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return clicked;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	private static String itemId(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key != null ? key.toString() : "";
	}
}
