package dev.kubeui.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/// The real menu for [KubeUIMachineBlockEntity] - four real slots (input/output/upgrade/fuel) at
/// the coordinates [KubeUIMachineScreen] draws its own custom panel/frames around (must stay in
/// exact lockstep with that screen's own layout constants - see the slot construction below), plus
/// [#PICKER_ROWS] fake preview slots backing the kind picker (see [#selectKind]) shown only while
/// [KubeUIMachineBlockEntity#STATUS_NO_KIND].
///
/// The picker reuses the exact same "fake `Slot`s populated server-side, real item sync does the
/// rest, `#clicked` intercepted for that index range" pattern already proven in
/// [KubeUITradeExecuteMenu] - deliberately not `AbstractContainerMenu#clickMenuButton` (real, but
/// would still need a way to get each kind's icon/name to the client, which real slot sync already
/// does for free) and deliberately a *fixed* slot count regardless of how many kinds actually exist
/// (unfilled rows just stay empty) - the picker slots are constructed identically whether this is
/// the server's real menu (built from a real, possibly server-only-populated [KubeUIMachines]
/// registry) or the client's own bare reconstruction (see the 2-arg constructor below); a menu's
/// slot *count* has to match between both sides regardless of what each currently has to put in
/// them, real content syncs down afterward the same way [KubeUITradeExecuteMenu]'s own real offers
/// already do.
///
/// Progress/max-progress/energy/max-energy/crafted/lit/status sync to the client via real
/// `ContainerData` (`addDataSlots`, the same mechanism `AbstractFurnaceMenu`'s own burn-time/cook-
/// time bars use) - on the server, [#data]'s `get(...)` always reads the live block entity fields
/// directly; on the client, there's no live block entity, so the synced values simply get written
/// back into the same small array by the network layer's own `set(...)` calls, then read back for
/// rendering.
final class KubeUIMachineMenu extends AbstractContainerMenu {
	static final int MACHINE_SLOT_COUNT = 4;
	static final int PICKER_ROWS = 4;
	static final int TOP_SLOT_COUNT = MACHINE_SLOT_COUNT + PICKER_ROWS;
	/// Card-style picker layout (redone from four bare 18x18 slots - "un GUI horrible" - into full-
	/// width rows) - [KubeUIMachineScreen] draws each card from these same constants, so the row's
	/// icon (a real `Slot`, positioned here) always lines up with the card frame around it.
	static final int PICKER_CARD_X = 10;
	static final int PICKER_CARD_Y = 24;
	static final int PICKER_CARD_WIDTH = 156;
	static final int PICKER_CARD_HEIGHT = 18;
	static final int PICKER_CARD_GAP = 2;
	static final int PICKER_ROW_STRIDE = PICKER_CARD_HEIGHT + PICKER_CARD_GAP;
	static final int PICKER_ICON_X = PICKER_CARD_X + 3;
	static final int PICKER_ICON_Y = PICKER_CARD_Y + 1;

	private final KubeUIMachineBlockEntity machine;
	private final SimpleContainer pickerContainer = new SimpleContainer(PICKER_ROWS);
	private final List<KubeUIMachines.Kind> availableKinds;
	private final int[] clientData = new int[7];

	KubeUIMachineMenu(int containerId, Inventory inventory, KubeUIMachineBlockEntity machine) {
		super(KubeUIMenus.MACHINE.get(), containerId);
		this.machine = machine;
		this.availableKinds = KubeUIMachines.allKinds();

		// These coordinates must stay in exact lockstep with KubeUIMachineScreen's own
		// SLOT_UPGRADE_X/Y, SLOT_INPUT_X/Y, SLOT_FUEL_X/Y, SLOT_OUTPUT_X/Y and INV_X/INV_Y constants -
		// vanilla renders each slot's real item at the Slot's own x/y, while the screen separately
		// draws the slot *frame* behind it; letting the two drift apart is exactly what put every
		// item and label at the wrong spot in a real, reported screenshot.
		addSlot(new Slot(machine, KubeUIMachineBlockEntity.SLOT_UPGRADE, 8, 38));
		addSlot(new Slot(machine, KubeUIMachineBlockEntity.SLOT_INPUT, 56, 38));
		addSlot(new Slot(machine, KubeUIMachineBlockEntity.SLOT_FUEL, 56, 74) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return machine.canPlaceItem(KubeUIMachineBlockEntity.SLOT_FUEL, stack);
			}
		});
		addSlot(new Slot(machine, KubeUIMachineBlockEntity.SLOT_OUTPUT, 126, 56) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}
		});

		// A kind's icon is its recipe's real output item, tagged with the kind's own display name
		// (`DataComponents.CUSTOM_NAME`) - the same real component [KubeUIActions#giveMachineItem]
		// already stamps onto a properly-given item - so the screen can read a row's label straight
		// off the synced `ItemStack` (`Slot#getItem().getHoverName()`) rather than needing its own
		// separate sync channel just for text.
		for (int i = 0; i < availableKinds.size() && i < PICKER_ROWS; i++) {
			var kind = availableKinds.get(i);
			var outputItem = KubeUICurrency.resolveItem(kind.recipe().outputItem());
			if (outputItem != null) {
				var stack = new ItemStack(outputItem);
				stack.set(DataComponents.CUSTOM_NAME, Component.literal(kind.displayName()));
				pickerContainer.setItem(i, stack);
			}
		}
		for (int i = 0; i < PICKER_ROWS; i++) {
			addSlot(new Slot(pickerContainer, i, PICKER_ICON_X, PICKER_ICON_Y + i * PICKER_ROW_STRIDE) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return false;
				}

				@Override
				public boolean mayPickup(Player player) {
					return false;
				}

				@Override
				public boolean isFake() {
					return true;
				}
			});
		}

		addStandardInventorySlots(inventory, 8, 106);

		addDataSlots(new ContainerData() {
			@Override
			public int get(int id) {
				return switch (id) {
					case 0 -> machine.progress();
					case 1 -> machine.processTicks();
					case 2 -> machine.energy();
					case 3 -> machine.maxEnergy();
					case 4 -> (int) machine.crafted();
					case 5 -> machine.lit() ? 1 : 0;
					case 6 -> machine.status();
					default -> 0;
				};
			}

			@Override
			public void set(int id, int value) {
				if (id >= 0 && id < clientData.length) {
					clientData[id] = value;
				}
			}

			@Override
			public int getCount() {
				return clientData.length;
			}
		});
	}

	/// The client's own bare reconstruction from `ClientboundOpenScreenPacket` needs a real,
	/// throwaway [KubeUIMachineBlockEntity] purely as a data holder for the menu's slots/data
	/// slots - `BlockEntity`'s own constructor (confirmed by a real client-side crash:
	/// `IllegalStateException: Invalid block entity kubeui:machine ... got Block{minecraft:air}`)
	/// validates that the given `BlockState` actually belongs to this block entity type's real
	/// block, so this needs `KubeUIBlocks.MACHINE`'s own default state, not `Blocks.AIR`'s.
	KubeUIMachineMenu(int containerId, Inventory inventory) {
		this(containerId, inventory, new KubeUIMachineBlockEntity(net.minecraft.core.BlockPos.ZERO, KubeUIBlocks.MACHINE.get().defaultBlockState()));
	}

	int progress() {
		return clientData[0];
	}

	int maxProgress() {
		return Math.max(1, clientData[1]);
	}

	int energy() {
		return clientData[2];
	}

	int maxEnergy() {
		return Math.max(1, clientData[3]);
	}

	int crafted() {
		return clientData[4];
	}

	boolean lit() {
		return clientData[5] != 0;
	}

	int status() {
		return clientData[6];
	}

	KubeUIMachineBlockEntity machine() {
		return machine;
	}

	/// Left-clicking a populated picker row assigns this block that kind - same real "intercept a
	/// specific slot-index range in `#clicked`" pattern [KubeUITradeExecuteMenu#clicked] already
	/// uses for its own offer rows, deliberately not vanilla's `clickMenuButton`/
	/// `ServerboundContainerButtonClickPacket` machinery (real, but tied to specific vanilla menus
	/// server-side, and still wouldn't get a picker row's icon/name to the client without the exact
	/// same slot-sync this already gets for free). Clearing the picker's own items afterward means
	/// nothing lingers on screen once [KubeUIMachineScreen] stops drawing the picker (an empty fake
	/// slot simply renders nothing, vanilla's own per-frame item-render pass draws every slot
	/// unconditionally and isn't otherwise skippable from here).
	@Override
	public void clicked(int slotIndex, int button, ContainerInput type, Player clickingPlayer) {
		if (slotIndex >= MACHINE_SLOT_COUNT && slotIndex < TOP_SLOT_COUNT && type == ContainerInput.PICKUP
			&& clickingPlayer instanceof net.minecraft.server.level.ServerPlayer) {
			selectKind(slotIndex - MACHINE_SLOT_COUNT);
			return;
		}
		super.clicked(slotIndex, button, type, clickingPlayer);
	}

	private void selectKind(int index) {
		if (index < 0 || index >= availableKinds.size()) {
			return;
		}
		machine.setKind(availableKinds.get(index).id());
		for (int i = 0; i < PICKER_ROWS; i++) {
			pickerContainer.setItem(i, ItemStack.EMPTY);
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex >= MACHINE_SLOT_COUNT && slotIndex < TOP_SLOT_COUNT) {
			return ItemStack.EMPTY;
		}

		ItemStack clicked = ItemStack.EMPTY;
		Slot slot = this.slots.get(slotIndex);
		if (slot != null && slot.hasItem()) {
			ItemStack stack = slot.getItem();
			clicked = stack.copy();
			if (slotIndex < MACHINE_SLOT_COUNT) {
				if (!this.moveItemStackTo(stack, TOP_SLOT_COUNT, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else if (!this.moveItemStackTo(stack, 0, MACHINE_SLOT_COUNT, false)) {
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
