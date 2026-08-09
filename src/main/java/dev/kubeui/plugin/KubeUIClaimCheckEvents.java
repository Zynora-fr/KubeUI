package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIClaims;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// Periodic per-player claim-intrusion check - same real shape [KubeUIStructureZoneEvents]/
/// [KubeUIGuildTerritoryEvents] already use for their own zones.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIClaimCheckEvents {
	static final int CHECK_INTERVAL_TICKS = 20;

	private KubeUIClaimCheckEvents() {
	}

	@SubscribeEvent
	static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
			return;
		}
		KubeUIClaims.check(player);
	}
}
