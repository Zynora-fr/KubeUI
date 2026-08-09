package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/// Server lifecycle wiring for [KubeUIClaims] - same real "load on start, periodic autosave, final
/// flush on stop" convention [KubeUICurrencyEvents]/[KubeUIGuildEvents] already established.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIClaimEvents {
	private static final int AUTOSAVE_INTERVAL_TICKS = 600;

	private KubeUIClaimEvents() {
	}

	@SubscribeEvent
	static void onServerAboutToStart(ServerAboutToStartEvent event) {
		KubeUIClaims.load(event.getServer());
	}

	@SubscribeEvent
	static void onServerStopping(ServerStoppingEvent event) {
		KubeUIClaims.save();
	}

	@SubscribeEvent
	static void onServerTick(ServerTickEvent.Post event) {
		if (event.getServer().getTickCount() % AUTOSAVE_INTERVAL_TICKS == 0 && KubeUIClaims.isDirty()) {
			KubeUIClaims.save();
		}
	}
}
