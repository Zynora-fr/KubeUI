package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIGuildTerritory;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// Periodic per-player guild-territory check - same real shape [KubeUIStructureZoneEvents] already
/// uses for its own zones.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIGuildTerritoryEvents {
	private static final int CHECK_INTERVAL_TICKS = 20;

	private KubeUIGuildTerritoryEvents() {
	}

	@SubscribeEvent
	static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		KubeUIGuildTerritory.check(player);
	}
}
