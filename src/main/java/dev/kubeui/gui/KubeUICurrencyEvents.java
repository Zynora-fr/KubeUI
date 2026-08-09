package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/// Server lifecycle/tick wiring for [KubeUICurrency]/[KubeUIShop] - loading the currency ledger on
/// world start, periodically flushing it to disk (see [KubeUICurrency]'s class docs for why that's
/// periodic rather than per-transaction), a final flush on shutdown so a normal stop never loses
/// anything, and driving [KubeUIShop#tickFluctuation]. One global server tick, not
/// [net.neoforged.neoforge.event.tick.PlayerTickEvent] like [KubeUIQuestEvents] - price
/// fluctuation and the ledger autosave are both server-wide state, not per-player, and would
/// otherwise redundantly re-check once per online player per interval for no reason.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUICurrencyEvents {
	/// 30 real seconds (600 ticks) between ledger autosaves - frequent enough that a crash loses
	/// very little, infrequent enough that a busy shop server isn't doing disk I/O every tick.
	private static final int AUTOSAVE_INTERVAL_TICKS = 600;

	private KubeUICurrencyEvents() {
	}

	@SubscribeEvent
	static void onServerAboutToStart(ServerAboutToStartEvent event) {
		KubeUICurrency.load(event.getServer());
	}

	@SubscribeEvent
	static void onServerStopping(ServerStoppingEvent event) {
		KubeUICurrency.save();
	}

	@SubscribeEvent
	static void onServerTick(ServerTickEvent.Post event) {
		long tick = event.getServer().getTickCount();

		KubeUIShop.tickFluctuation(tick);

		if (tick % AUTOSAVE_INTERVAL_TICKS == 0 && KubeUICurrency.isDirty()) {
			KubeUICurrency.save();
		}
	}
}
