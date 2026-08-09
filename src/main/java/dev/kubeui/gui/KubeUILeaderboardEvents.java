package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/// Server lifecycle/tick wiring for [KubeUILeaderboards] - same real "load on start, periodic
/// autosave, final flush on stop" convention [KubeUIGuildEvents]/[KubeUICurrencyEvents] already
/// established for their own disk-backed registries.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUILeaderboardEvents {
	private static final int AUTOSAVE_INTERVAL_TICKS = 600;

	private KubeUILeaderboardEvents() {
	}

	@SubscribeEvent
	static void onServerAboutToStart(ServerAboutToStartEvent event) {
		KubeUILeaderboards.load(event.getServer());
	}

	@SubscribeEvent
	static void onServerStopping(ServerStoppingEvent event) {
		KubeUILeaderboards.save();
	}

	@SubscribeEvent
	static void onServerTick(ServerTickEvent.Post event) {
		if (event.getServer().getTickCount() % AUTOSAVE_INTERVAL_TICKS == 0 && KubeUILeaderboards.isDirty()) {
			KubeUILeaderboards.save();
		}
	}
}
