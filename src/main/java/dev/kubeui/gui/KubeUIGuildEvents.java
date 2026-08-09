package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/// Server lifecycle/tick wiring for [KubeUIGuilds] - same real "load on start, periodic autosave,
/// final flush on stop" convention [KubeUICurrencyEvents] already established for its own ledger -
/// plus driving [KubeUIGuildScheduledEvents#tick] (server-wide state, one global tick rather than
/// per-player). Guild territory entry alerts are per-player instead, see
/// [KubeUIGuildTerritoryEvents] (same shape [KubeUIStructureZoneEvents] already uses).
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIGuildEvents {
	private static final int AUTOSAVE_INTERVAL_TICKS = 600;

	private KubeUIGuildEvents() {
	}

	@SubscribeEvent
	static void onServerAboutToStart(ServerAboutToStartEvent event) {
		KubeUIGuilds.load(event.getServer());
	}

	@SubscribeEvent
	static void onServerStopping(ServerStoppingEvent event) {
		KubeUIGuilds.save();
	}

	@SubscribeEvent
	static void onServerTick(ServerTickEvent.Post event) {
		long tick = event.getServer().getTickCount();

		KubeUIGuildScheduledEvents.tick(event.getServer(), tick);

		if (tick % AUTOSAVE_INTERVAL_TICKS == 0 && KubeUIGuilds.isDirty()) {
			KubeUIGuilds.save();
		}
	}
}
