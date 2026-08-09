package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/// Wires [KubeUIMenus#MACHINE] to [KubeUIMachineScreen] - same real `RegisterMenuScreensEvent`
/// mechanism as [KubeUIStorageScreenHook]/[KubeUIRecipeGridScreenHook].
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIMachineScreenHook {
	private KubeUIMachineScreenHook() {
	}

	@SubscribeEvent
	static void onRegisterScreens(RegisterMenuScreensEvent event) {
		event.register(KubeUIMenus.MACHINE.get(), KubeUIMachineScreen::new);
	}
}
