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

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
	}
}
