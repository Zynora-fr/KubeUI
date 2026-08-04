package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIScreen;
import dev.kubeui.gui.KubeUIScreenBuilder;
import dev.kubeui.gui.KubeUIScreenInjector;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/// Fires `KubeUIScreenInjector.register(...)`'s callback (if any is registered for the opening
/// screen's class) on `ScreenEvent.Init.Post` - the same injection point [KubeUISidebarInjector]
/// uses for `InventoryScreen` specifically, generalized to whatever class was registered. Builds
/// the configured panel via a detached `KubeUIScreen` (never `Minecraft.setScreen`'d - see
/// `KubeUIScreen#buildDetached`) and adds its widgets straight onto the real screen.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIScreenInjectorHandler {
	private KubeUIScreenInjectorHandler() {
	}

	@SubscribeEvent
	static void onScreenInit(ScreenEvent.Init.Post event) {
		var configure = KubeUIScreenInjector.forScreen(event.getScreen());
		if (configure == null) {
			return;
		}

		var panel = KubeUIScreenBuilder.builder("");
		configure.accept(event.getScreen(), panel);

		var detached = new KubeUIScreen(panel);
		for (var widget : detached.buildDetached(event.getScreen().width, event.getScreen().height)) {
			event.addListener(widget);
		}
	}
}
