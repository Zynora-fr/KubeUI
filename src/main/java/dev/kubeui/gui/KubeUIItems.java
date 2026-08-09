package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/// Real NeoForge item registration (`DeferredRegister.createItems`, the standard mechanism for a
/// custom item) for [KubeUITraderEggItem] - the custom trader system's real, giftable item, not a
/// tag applied to an existing entity (see [KubeUITraderDesigner]'s class doc for why).
public final class KubeUIItems {
	private KubeUIItems() {
	}

	private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(KubeUI.MOD_ID);

	static final DeferredItem<KubeUITraderEggItem> TRADER_EGG = ITEMS.registerItem("trader_egg", KubeUITraderEggItem::new);

	/// [KubeUIBackpackItem].
	static final DeferredItem<KubeUIBackpackItem> BACKPACK = ITEMS.registerItem("backpack", KubeUIBackpackItem::new);

	/// The real `BlockItem` for [KubeUIStorageBlock] - without this, the block exists
	/// as a registry entry but a player has no way to ever obtain/place one in survival.
	static final DeferredItem<net.minecraft.world.item.BlockItem> STORAGE_CRATE = ITEMS.registerSimpleBlockItem(KubeUIBlocks.STORAGE);

	/// The real `BlockItem` for [KubeUIMachineBlock] - `KubeUIActions.giveMachineItem(...)`
	/// is the real way a player gets one already set to a specific kind; a plain `/give` gives an
	/// unconfigured block (empty `kind`, does nothing until a script sets one).
	static final DeferredItem<net.minecraft.world.item.BlockItem> MACHINE = ITEMS.registerSimpleBlockItem(KubeUIBlocks.MACHINE);

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}
}
