package dev.kubeui.gui;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Backs `KubeUIActions.defineMachine(...)`/`.registerMachineUpgrade(...)` - kind/upgrade
/// definitions are an in-memory registry re-declared every server
/// boot, same convention as every other `KubeUIActions`-backed registry ([KubeUIDialogue],
/// [KubeUIShop], ...). A single real `kubeui:machine` block/block-entity/menu/screen (see
/// [KubeUIMachineBlockEntity]) serves every registered kind, NOT one real `Block` per kind - real
/// NeoForge registries are frozen long before any `server_scripts` reload could define a new one, so
/// a script genuinely can't register its own distinct block/item type this way, the same real
/// constraint [KubeUITraderEggItem] (one item, many script-defined trader configs) already works
/// within. Which kind a given placed block *is* lives on the block entity's own persisted `kind`
/// string, copied from the placing item's real `DataComponents.CUSTOM_DATA` (see
/// [KubeUIMachineBlock#setPlacedBy]) - and [KubeUIActions#giveMachineItem] also stamps the item's
/// real `DataComponents.CUSTOM_NAME` from this registry so two different kinds already read as two
/// different things in the inventory/hotbar, not just after placing one and opening its screen.
final class KubeUIMachines {
	record Recipe(String inputItem, int inputCount, String outputItem, int outputCount, int processTicks, int energyPerTick) {
	}

	record Kind(String id, String displayName, Recipe recipe, String redstoneMode) {
	}

	record Upgrade(String itemId, double speedMultiplier, double yieldBonus) {
	}

	private static final Map<String, Kind> KINDS = new ConcurrentHashMap<>();
	private static final Map<String, Upgrade> UPGRADES = new ConcurrentHashMap<>();

	private KubeUIMachines() {
	}

	static void defineKind(String id, String displayName, String inputItem, int inputCount, String outputItem, int outputCount, int processTicks, int energyPerTick, String redstoneMode) {
		var recipe = new Recipe(inputItem, Math.max(1, inputCount), outputItem, Math.max(1, outputCount), Math.max(1, processTicks), Math.max(0, energyPerTick));
		KINDS.put(id, new Kind(id, displayName, recipe, redstoneMode == null ? "ignore" : redstoneMode));
	}

	static Kind kind(String id) {
		return KINDS.get(id);
	}

	/// Every currently-registered kind, in registration order - backs the machine screen's real
	/// in-GUI kind picker (see [KubeUIMachineMenu]) for a block placed with no kind (or an
	/// unregistered one) set: a player who only ever finds a generic, unconfigured `kubeui:machine`
	/// item (creative inventory, JEI, ...) - not one already stamped via
	/// [dev.kubeui.gui.KubeUIActions#giveMachineItem] - otherwise has no way to choose "Crusher" vs
	/// "Smelter" at all, since a script can't register a second real `Block` type at runtime.
	static List<Kind> allKinds() {
		return List.copyOf(KINDS.values());
	}

	static void registerUpgrade(String itemId, double speedMultiplier, double yieldBonus) {
		UPGRADES.put(itemId, new Upgrade(itemId, Math.max(0.1, speedMultiplier), Math.max(0, yieldBonus)));
	}

	static Upgrade upgrade(String itemId) {
		return UPGRADES.get(itemId);
	}
}
