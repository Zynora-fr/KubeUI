package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/// The real, physical crafting-grid menu behind the Recipe Designer (`/kubeui recipe-designer`) -
/// genuinely interactive vanilla [Slot]s the player can drag any item into/out of from their own
/// live inventory. Two things were tried and rejected before this one, both verified against real
/// decompiled sources rather than assumed:
/// - KubeJS's own `ChestMenuData`/`ChestMenuSlot` "chest GUI" system is a read-only button/icon
///   display grid - `ChestMenuContainerSlot#mayPlace`/`#mayPickup` are hardcoded `false` and
///   `CustomChestMenu#clicked` never falls through to normal vanilla pickup/place logic for it, so
///   nothing could ever actually be dragged in.
/// - Reusing vanilla's own `ChestMenu` via the shared `MenuType.GENERIC_9x3` worked functionally,
///   but looks like a plain chest for every kind - the player asked for each kind to look like its
///   real vanilla counterpart (crafting table, furnace, blast furnace, smoker, stonecutter,
///   smithing table) instead.
///
/// So this is its own small [AbstractContainerMenu], registered per kind-family in [KubeUIMenus]
/// (one `MenuType` each, since vanilla's `MenuScreens` hardcodes every *shared* `MenuType` to one
/// specific screen with no way to re-skin it per use). Slot pixel positions per kind are the exact
/// real coordinates vanilla's own menus use for that block (see [#gridPositions]/[#outputPosition]),
/// so each matching [KubeUIRecipeGridScreen] background texture lines up correctly - but every
/// slot, including the result, is a plain, fully-interactive [Slot], never vanilla's read-only,
/// recipe-computed `ResultSlot`/`FurnaceResultSlot` (which only ever shows an item for a recipe
/// that's *already* registered - useless for defining a brand new one). The one special behavior:
/// a left click on the result slot while it holds an item captures the grid and asks
/// [KubeUIRecipeDesignerGrid] to save it, before falling through to the normal pickup so the click
/// still behaves like a real slot interaction either way.
final class KubeUIRecipeGridMenu extends AbstractContainerMenu {
	static final int OUTPUT_INDEX = 0;
	private static final int GRID_START = 1;

	private final String kind;
	private final int topSlotCount;

	KubeUIRecipeGridMenu(int containerId, Inventory inventory, String kind) {
		super(KubeUIMenus.menuTypeFor(kind), containerId);
		this.kind = kind;

		var gridPositions = gridPositions(kind);
		var outputPosition = outputPosition(kind);
		this.topSlotCount = GRID_START + gridPositions.length;

		var craftContainer = new SimpleContainer(topSlotCount);
		addSlot(new Slot(craftContainer, OUTPUT_INDEX, outputPosition[0], outputPosition[1]));
		for (int i = 0; i < gridPositions.length; i++) {
			addSlot(new Slot(craftContainer, GRID_START + i, gridPositions[i][0], gridPositions[i][1]));
		}
		addStandardInventorySlots(inventory, 8, 84);
	}

	/// Real vanilla pixel positions for the ingredient slot(s), by kind - `CraftingMenu`'s 3x3 grid
	/// (`30 + x*18, 17 + y*18`) for the crafting-family kinds, `AbstractFurnaceMenu`'s single
	/// ingredient slot (`56, 17`, shared identically by furnace/blast furnace/smoker - only the
	/// texture differs between them) for the cooking kinds, `StonecutterMenu`'s input (`20, 33`),
	/// `SmithingMenu`'s three slots (`8, 48`/`26, 48`/`44, 48`, template/base/addition in that
	/// order) for smithing.
	private static int[][] gridPositions(String kind) {
		return switch (kind) {
			case "shapeless", "shaped" -> new int[][]{
				{30, 17}, {48, 17}, {66, 17},
				{30, 35}, {48, 35}, {66, 35},
				{30, 53}, {48, 53}, {66, 53}
			};
			case "smithing" -> new int[][]{{8, 48}, {26, 48}, {44, 48}};
			case "stonecutting" -> new int[][]{{20, 33}};
			default -> new int[][]{{56, 17}};
		};
	}

	private static int[] outputPosition(String kind) {
		return switch (kind) {
			case "shapeless", "shaped" -> new int[]{124, 35};
			case "smithing" -> new int[]{98, 48};
			case "stonecutting" -> new int[]{143, 33};
			default -> new int[]{116, 35};
		};
	}

	/// Which of the grid slot indices (starting at [#GRID_START]) hold ingredients for `kind` -
	/// every grid slot this menu has for its kind, since each kind's layout is now sized exactly
	/// right for what it means (no leftover/ignored cells like the old shared-grid design had).
	static int[] inputIndexes(String kind) {
		int count = gridPositions(kind).length;
		var indexes = new int[count];
		for (int i = 0; i < count; i++) {
			indexes[i] = GRID_START + i;
		}
		return indexes;
	}

	@Override
	public void clicked(int slotIndex, int button, ContainerInput type, Player player) {
		if (slotIndex == OUTPUT_INDEX && type == ContainerInput.PICKUP && button == 0
			&& getSlot(OUTPUT_INDEX).hasItem() && player instanceof ServerPlayer serverPlayer
			&& KubeUIRecipeDesignerGrid.tryCaptureAndSave(this, kind, serverPlayer)) {
			return;
		}

		super.clicked(slotIndex, button, type, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex < topSlotCount) {
				if (!this.moveItemStackTo(stack, topSlotCount, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, topSlotCount, false)) {
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
}
