package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/// Wires [KubeUIMenus#STORAGE] to [KubeUIStorageScreen], and [KubeUIMenus#BACKPACK] to real
/// vanilla `ContainerScreen` (`net.minecraft.client.gui.screens.inventory.ContainerScreen`,
/// decompiled and confirmed - the same class already reused for [KubeUIMenus#STORAGE]'s own
/// menu-level plumbing) - a backpack needs no sort/search/settings extras, so unlike storage it
/// doesn't need its own screen subclass, just a real registration (missing entirely before was a
/// real bug: "Failed to create screen for menu type: kubeui:backpack" on every open, confirmed
/// from a live client log). Same real `RegisterMenuScreensEvent` mechanism, same `Dist.CLIENT`
/// gating, as [KubeUIRecipeGridScreenHook].
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIStorageScreenHook {
	private KubeUIStorageScreenHook() {
	}

	@SubscribeEvent
	static void onRegisterScreens(RegisterMenuScreensEvent event) {
		event.register(KubeUIMenus.STORAGE.get(), KubeUIStorageScreen::new);
		event.register(KubeUIMenus.BACKPACK.get(), net.minecraft.client.gui.screens.inventory.ContainerScreen::new);
	}
}
