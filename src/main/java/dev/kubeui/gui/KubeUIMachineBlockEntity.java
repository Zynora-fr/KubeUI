package dev.kubeui.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/// The real, in-world-persisted container/tick logic behind `kubeui:machine` - one
/// real block/block-entity/menu/screen generalizing a script-defined "kind" (a single-input,
/// single-output, N-tick recipe - deliberately simpler than a multi-input recipe, to keep the real
/// fixed 4-slot layout - input/output/upgrade/fuel - honest rather than needing a per-kind dynamic
/// menu) rather than one Java class per machine type. `kind` is set once, at placement, copied from
/// the placing item's real `DataComponents.CUSTOM_DATA` (see [KubeUIMachineBlock#setPlacedBy]) - the
/// same component [KubeUITraderEggItem] already uses for its own per-item data.
///
/// Bundles this batch's other features directly onto the one real tick loop ([#serverTick]) rather
/// than separate classes, the same reasoning [KubeUIStorageBlockEntity] already used for its own
/// batch: [#outputLinkPos] (logical chaining - scriptable, not physical/redstone),
/// `WorldlyContainer` (real hopper/pipe compatibility), [#energy]/[#maxEnergy]/[#tickFuel] (a real
/// vanilla-furnace-shaped fuel slot backing a neutral `receiveEnergy`-shaped counter - see their own
/// docs), [#crafted]/[#activeTicks]/[#totalTicks] (stats), the upgrade slot, [#networkId], and
/// [#maybeSendAlert].
public final class KubeUIMachineBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
	static final int SLOT_COUNT = 4;
	static final int SLOT_INPUT = 0;
	static final int SLOT_OUTPUT = 1;
	static final int SLOT_UPGRADE = 2;
	static final int SLOT_FUEL = 3;
	private static final int[] ALL_SLOTS = {SLOT_INPUT, SLOT_OUTPUT, SLOT_UPGRADE, SLOT_FUEL};
	private static final long ALERT_THROTTLE_MS = 30_000;
	/// How much [#energy] one tick of real vanilla burn time is worth - a lava bucket (20000 real
	/// burn ticks) is worth 20000 energy this way, comfortably fueling a `maxEnergy`-1000 machine
	/// many times over before running dry, the same "one bucket lasts a long time" feel vanilla
	/// furnaces have.
	private static final int ENERGY_PER_BURN_TICK = 1;

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
	private String kind = "";
	private int progress;
	private int energy;
	private int maxEnergy = 1000;
	private int litTime;
	private long crafted;
	private long activeTicks;
	private long totalTicks;
	private BlockPos outputLinkPos;
	private String networkId = "";
	private long lastAlertAtMs;
	/// Purely transient (never saved/loaded - recomputed fresh every tick) summary of *why* the
	/// machine isn't actively processing right now, synced to the client so the screen can say so
	/// directly instead of a player only finding out from a throttled chat alert every 30 seconds
	/// ("je met cobblestone dans Input il se passe rien" - real reported confusion, not guessed).
	/// See [#STATUS_*] for the meaning of each code.
	private int status;

	/// No kind chosen yet - the *only* status that makes [KubeUIMachineScreen] show the kind picker.
	static final int STATUS_NO_KIND = 0;
	/// A kind *is* assigned but doesn't resolve to anything real right now (unregistered kind id, or
	/// a registered kind whose recipe item ids don't resolve) - real, reported bug this exists to fix:
	/// this used to share [#STATUS_NO_KIND]'s code, so the picker kept reappearing every time a
	/// player reopened an *already-kinded* "Crusher" machine whose recipe items momentarily failed to
	/// resolve, silently discarding the kind the player already chose. Kept as its own status (shown
	/// as a plain error line, not the picker) so a script misconfiguration reads as "this machine's
	/// kind is broken" rather than "pick again".
	static final int STATUS_INVALID_KIND = 1;
	static final int STATUS_REDSTONE_BLOCKED = 2;
	static final int STATUS_WAITING_FOR_INPUT = 3;
	static final int STATUS_NEEDS_FUEL = 4;
	static final int STATUS_OUTPUT_FULL = 5;
	static final int STATUS_RUNNING = 6;

	public KubeUIMachineBlockEntity(BlockPos pos, BlockState state) {
		super(KubeUIBlockEntities.MACHINE.get(), pos, state);
	}

	// ---------------------------------------------------------------- BaseContainerBlockEntity hooks

	/// The real per-kind name (e.g. "Crusher") once `kind` resolves to a defined
	/// [KubeUIMachines.Kind] - resolved here, server-side, since [#getDefaultName] backs
	/// [net.minecraft.world.MenuProvider#getDisplayName], and that's what becomes the client's real
	/// screen title (sent once, already-resolved, inside `ClientboundOpenScreenPacket`) - the client
	/// never needs its own copy of the kind registry (which a dedicated server's remote client
	/// wouldn't have anyway, since `defineMachine` only ever runs server-side) just to know its own
	/// machine's name.
	@Override
	protected Component getDefaultName() {
		var def = KubeUIMachines.kind(kind);
		return def != null ? Component.literal(def.displayName()) : Component.translatable("block.kubeui.machine");
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

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new KubeUIMachineMenu(containerId, inventory, this);
	}

	// ---------------------------------------------------------------- WorldlyContainer

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return ALL_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
		return (slot == SLOT_INPUT || slot == SLOT_UPGRADE || slot == SLOT_FUEL) && canPlaceItem(slot, itemStack);
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
		return slot == SLOT_OUTPUT;
	}

	/// Rejects non-fuel items from [#SLOT_FUEL] - same real check both `Slot#mayPlace` (see
	/// [KubeUIMachineMenu]) and a hopper feeding this face need, backed by the same real
	/// `ItemStack#getBurnTime` NeoForge extension vanilla furnaces use (`AbstractFurnaceBlockEntity#isFuel`
	/// - `1.21.1` has no `Level#fuelValues()` registry, that's a newer refactor), not a hand-picked
	/// item list - any real fuel (coal, charcoal, a lava bucket, planks, ...) works here exactly
	/// like it would in a furnace.
	@Override
	public boolean canPlaceItem(int slot, ItemStack itemStack) {
		if (slot == SLOT_FUEL) {
			return net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(itemStack);
		}
		return true;
	}

	// ---------------------------------------------------------------- kind / accessors

	public void setKind(String kind) {
		this.kind = kind == null ? "" : kind;
		setChanged();
	}

	public String kind() {
		return kind;
	}

	int progress() {
		return progress;
	}

	int status() {
		return status;
	}

	int processTicks() {
		var def = KubeUIMachines.kind(kind);
		return def != null ? def.recipe().processTicks() : 1;
	}

	int energy() {
		return energy;
	}

	int maxEnergy() {
		return maxEnergy;
	}

	boolean lit() {
		return litTime > 0;
	}

	long crafted() {
		return crafted;
	}

	long activeTicks() {
		return activeTicks;
	}

	long totalTicks() {
		return totalTicks;
	}

	/// A minimal, real-shaped energy sink deliberately *not* wired to a live FE/RF
	/// capability (no such mod is actually present to verify a real bridge against -
	/// shipping one unverified would break the same discipline every other "audited, not built"
	/// entry in this project follows). Any script - including a future real energy-mod bridge - can
	/// feed this machine via this one method, the neutral integration point the roadmap asked for.
	public int receiveEnergy(int amount, boolean simulate) {
		int accepted = Math.min(amount, maxEnergy - energy);
		if (accepted > 0 && !simulate) {
			energy += accepted;
			setChanged();
		}
		return accepted;
	}

	/// The real fuel path ("le carburant c'est de la lave, du coal, charcoal etc" - a genuine
	/// vanilla-furnace-shaped fuel slot, not the earlier right-click-to-charge stand-in): burns
	/// whatever's in [#SLOT_FUEL] using the same real `level.fuelValues()` registry/burn-time table
	/// vanilla furnaces use, converting each remaining burn tick into [#ENERGY_PER_BURN_TICK] energy
	/// while there's room left in [#maxEnergy]. Mirrors vanilla `AbstractFurnaceBlockEntity#consumeFuel`
	/// exactly for the "does an empty bucket come back" behavior (`ItemStack#getCraftingRemainder`),
	/// not a hand-rolled guess.
	private void tickFuel(Level level) {
		if (litTime > 0) {
			litTime--;
			if (energy < maxEnergy) {
				energy = Math.min(maxEnergy, energy + ENERGY_PER_BURN_TICK);
				setChanged();
			}
			return;
		}

		if (energy >= maxEnergy) {
			return;
		}

		var fuel = items.get(SLOT_FUEL);
		if (fuel.isEmpty() || !net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity.isFuel(fuel)) {
			return;
		}

		int duration = fuel.getBurnTime(null);
		if (duration <= 0) {
			return;
		}

		boolean hasRemainder = fuel.hasCraftingRemainingItem();
		var remainder = hasRemainder ? fuel.getCraftingRemainingItem() : ItemStack.EMPTY;
		fuel.shrink(1);
		if (fuel.isEmpty()) {
			items.set(SLOT_FUEL, remainder);
		}
		litTime = duration;
		setChanged();
	}

	void setOutputLink(BlockPos pos) {
		this.outputLinkPos = pos;
		setChanged();
	}

	BlockPos outputLink() {
		return outputLinkPos;
	}

	void setNetworkId(String id) {
		KubeUIMachineNetwork.unregister(this);
		this.networkId = id == null ? "" : id;
		if (!this.networkId.isEmpty()) {
			KubeUIMachineNetwork.register(this.networkId, this);
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
			KubeUIMachineNetwork.register(networkId, this);
		}
	}

	/// Same real reasoning as [KubeUIStorageBlockEntity#setRemoved]'s own doc - this version has no
	/// generic vanilla "drop a container's contents on removal" hook, and `setRemoved()` (confirmed,
	/// via decompiled `LevelChunk`) never fires on a mere chunk unload, only on genuine removal.
	@Override
	public void setRemoved() {
		if (level != null && !level.isClientSide()) {
			net.minecraft.world.Containers.dropContents(level, worldPosition, items);
		}
		super.setRemoved();
		KubeUIMachineNetwork.unregister(this);
	}

	// ---------------------------------------------------------------- tick logic

	public static void serverTick(Level level, BlockPos pos, BlockState state, KubeUIMachineBlockEntity machine) {
		machine.totalTicks++;
		machine.tickFuel(level);

		if (machine.kind.isEmpty()) {
			machine.status = STATUS_NO_KIND;
			return;
		}

		var def = KubeUIMachines.kind(machine.kind);
		if (def == null) {
			machine.status = STATUS_INVALID_KIND;
			return;
		}

		boolean powered = level.hasNeighborSignal(pos);
		boolean shouldRun = switch (def.redstoneMode()) {
			case "requireSignal" -> powered;
			case "disableOnSignal" -> !powered;
			default -> true;
		};
		if (!shouldRun) {
			machine.status = STATUS_REDSTONE_BLOCKED;
			return;
		}

		var recipe = def.recipe();
		var inputItem = KubeUICurrency.resolveItem(recipe.inputItem());
		var outputItem = KubeUICurrency.resolveItem(recipe.outputItem());
		if (inputItem == null || outputItem == null) {
			machine.status = STATUS_INVALID_KIND;
			return;
		}

		var input = machine.items.get(SLOT_INPUT);
		if (!input.is(inputItem) || input.getCount() < recipe.inputCount()) {
			machine.progress = 0;
			machine.status = STATUS_WAITING_FOR_INPUT;
			return;
		}

		if (recipe.energyPerTick() > 0 && machine.energy < recipe.energyPerTick()) {
			machine.status = STATUS_NEEDS_FUEL;
			machine.maybeSendAlert(level, pos, "Out of energy");
			return;
		}

		var upgrade = machine.currentUpgrade();
		double speedMultiplier = upgrade != null ? upgrade.speedMultiplier() : 1.0;
		int effectiveProcessTicks = Math.max(1, (int) Math.round(recipe.processTicks() / speedMultiplier));
		int outputCount = recipe.outputCount() + (upgrade != null ? (int) upgrade.yieldBonus() : 0);

		var output = machine.items.get(SLOT_OUTPUT);
		boolean roomForOutput = output.isEmpty() || (output.is(outputItem) && output.getCount() + outputCount <= output.getMaxStackSize());
		if (!roomForOutput) {
			machine.status = STATUS_OUTPUT_FULL;
			machine.maybeSendAlert(level, pos, "Output full");
			return;
		}

		machine.status = STATUS_RUNNING;
		machine.activeTicks++;
		if (recipe.energyPerTick() > 0) {
			machine.energy -= recipe.energyPerTick();
		}
		machine.progress++;

		if (machine.progress >= effectiveProcessTicks) {
			machine.progress = 0;
			input.shrink(recipe.inputCount());
			if (output.isEmpty()) {
				machine.items.set(SLOT_OUTPUT, new ItemStack(outputItem, outputCount));
			} else {
				output.grow(outputCount);
			}
			machine.crafted++;
			machine.trySendToLink(level, outputItem);
		}

		machine.setChanged();
	}

	private KubeUIMachines.Upgrade currentUpgrade() {
		var stack = items.get(SLOT_UPGRADE);
		if (stack.isEmpty()) {
			return null;
		}
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key != null ? KubeUIMachines.upgrade(key.toString()) : null;
	}

	/// After a successful craft, tries pushing the produced output straight into
	/// [#outputLinkPos]'s own input slot (a purely logical link a script draws with
	/// [dev.kubeui.gui.KubeUIActions#linkMachineOutput], not a physical pipe) - only moves what the
	/// linked machine's input slot can actually accept (same item, room in the stack), never forces
	/// it through.
	private void trySendToLink(Level level, Item outputItem) {
		if (outputLinkPos == null) {
			return;
		}
		if (!(level.getBlockEntity(outputLinkPos) instanceof KubeUIMachineBlockEntity target)) {
			return;
		}
		var output = items.get(SLOT_OUTPUT);
		var targetInput = target.items.get(SLOT_INPUT);
		if (output.isEmpty()) {
			return;
		}
		if (targetInput.isEmpty()) {
			int moved = Math.min(output.getCount(), outputItem.getDefaultMaxStackSize());
			target.items.set(SLOT_INPUT, output.split(moved));
			target.setChanged();
		} else if (targetInput.is(outputItem) && targetInput.getCount() < targetInput.getMaxStackSize()) {
			int moved = Math.min(output.getCount(), targetInput.getMaxStackSize() - targetInput.getCount());
			targetInput.grow(moved);
			output.shrink(moved);
			target.setChanged();
		}
	}

	private void maybeSendAlert(Level level, BlockPos pos, String message) {
		long now = System.currentTimeMillis();
		if (now - lastAlertAtMs < ALERT_THROTTLE_MS) {
			return;
		}
		lastAlertAtMs = now;
		for (var player : level.players()) {
			if (player instanceof ServerPlayer serverPlayer && serverPlayer.blockPosition().closerThan(pos, 32)) {
				KubeUIMachineAlerts.send(serverPlayer, "Machine at " + pos.toShortString() + ": " + message);
			}
		}
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void saveAdditional(CompoundTag output, HolderLookup.Provider registries) {
		super.saveAdditional(output, registries);
		ContainerHelper.saveAllItems(output, items, registries);
		output.putString("kind", kind);
		output.putInt("progress", progress);
		output.putInt("energy", energy);
		output.putInt("maxEnergy", maxEnergy);
		output.putInt("litTime", litTime);
		output.putLong("crafted", crafted);
		output.putLong("activeTicks", activeTicks);
		output.putLong("totalTicks", totalTicks);
		output.putString("networkId", networkId);
		if (outputLinkPos != null) {
			output.putIntArray("outputLink", new int[]{outputLinkPos.getX(), outputLinkPos.getY(), outputLinkPos.getZ()});
		}
	}

	@Override
	protected void loadAdditional(CompoundTag input, HolderLookup.Provider registries) {
		super.loadAdditional(input, registries);
		items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(input, items, registries);
		kind = KubeUINbtCompat.getStringOr(input, "kind", "");
		progress = KubeUINbtCompat.getIntOr(input, "progress", 0);
		energy = KubeUINbtCompat.getIntOr(input, "energy", 0);
		maxEnergy = KubeUINbtCompat.getIntOr(input, "maxEnergy", 1000);
		litTime = KubeUINbtCompat.getIntOr(input, "litTime", 0);
		crafted = KubeUINbtCompat.getLongOr(input, "crafted", 0);
		activeTicks = KubeUINbtCompat.getLongOr(input, "activeTicks", 0);
		totalTicks = KubeUINbtCompat.getLongOr(input, "totalTicks", 0);
		networkId = KubeUINbtCompat.getStringOr(input, "networkId", "");
		var linkArray = input.getIntArray("outputLink");
		outputLinkPos = linkArray.length == 3 ? new BlockPos(linkArray[0], linkArray[1], linkArray[2]) : null;
	}
}
