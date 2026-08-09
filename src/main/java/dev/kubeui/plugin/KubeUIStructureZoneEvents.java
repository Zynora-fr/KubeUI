package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIStructureZones;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// Periodic (every [#CHECK_INTERVAL_TICKS]) server-side proximity check against every
/// [KubeUIStructureZones]-defined zone - real `PlayerTickEvent.Post`, server-side checked, same
/// "not every tick, a popup doesn't need sub-second precision" periodic-check shape
/// [dev.kubeui.gui.KubeUIMapClientEvents] already uses client-side for its own background sampling.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIStructureZoneEvents {
	static final int CHECK_INTERVAL_TICKS = 20;

	private KubeUIStructureZoneEvents() {
	}

	@SubscribeEvent
	static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		KubeUIStructureZones.check(player);
	}
}
