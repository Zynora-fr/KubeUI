package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/// Wires [KubeUIMenus#TRADER_GRID]/[KubeUIMenus#TRADE_EXECUTE] to their real screens via
/// NeoForge's real `RegisterMenuScreensEvent` (a mod-bus event) - `Dist.CLIENT`-gated like
/// [KubeUIRecipeGridScreenHook], so none of this (or the client-only screens it references) is
/// even loaded on a dedicated server.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUITraderGridScreenHook {
	private KubeUITraderGridScreenHook() {
	}

	@SubscribeEvent
	static void onRegisterScreens(RegisterMenuScreensEvent event) {
		event.register(KubeUIMenus.TRADER_GRID.get(), KubeUITraderGridScreen::new);
		event.register(KubeUIMenus.TRADE_EXECUTE.get(), KubeUITradeExecuteScreen::new);
	}
}
