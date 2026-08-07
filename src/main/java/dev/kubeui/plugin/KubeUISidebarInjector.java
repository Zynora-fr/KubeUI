package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUISidebar;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/// Adds KubeUISidebar's icons to the survival inventory screen (a Paladium-style icon bar) - via
/// NeoForge's `ScreenEvent.Init.Post`, the standard way to inject extra widgets into a vanilla
/// screen without a mixin. Fired on the main NeoForge event bus (not the mod bus), but that
/// doesn't need any special handling here - `@EventBusSubscriber` auto-detects it, exactly like
/// `RegisterClientCommandsEvent` in [KubeUIDebugCommands].
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUISidebarInjector {
	private static final int GAP = 4;
	private static final int EDGE_MARGIN = 6;

	private KubeUISidebarInjector() {
	}

	@SubscribeEvent
	static void onScreenInit(ScreenEvent.Init.Post event) {
		if (!(event.getScreen() instanceof InventoryScreen inventoryScreen)) {
			return;
		}

		AbstractContainerScreen<?> containerScreen = inventoryScreen;
		int x = containerScreen.getGuiLeft() - KubeUISidebar.ICON_SIZE - EDGE_MARGIN;
		int y = containerScreen.getGuiTop();

		for (var widget : KubeUISidebar.createIconWidgets(x, y, GAP)) {
			event.addListener(widget);
		}
	}
}
