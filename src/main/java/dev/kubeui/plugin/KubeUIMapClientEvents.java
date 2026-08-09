package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// Background "surface" layer sampling for [dev.kubeui.gui.KubeUIExploredMapCache] -
/// this MC version has no dedicated client tick event (confirmed: `net.neoforged.neoforge.event.
/// tick` only has `Server`/`Player`/`Entity`/`LevelTickEvent`), so this reuses `PlayerTickEvent.Post`
/// client-side-checked, the exact same real mechanism [dev.kubeui.gui.KubeUIQuestEvents] already
/// uses for its own periodic per-player check.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIMapClientEvents {
	/// Every 2 real seconds (40 ticks) is plenty for a cell the player is merely standing in to
	/// eventually get marked explored - this isn't trying to catch every cell a fast-moving player
	/// passes through, just steadily grow the revealed area during normal play.
	private static final int SAMPLE_INTERVAL_TICKS = 40;

	private KubeUIMapClientEvents() {
	}

	@SubscribeEvent
	static void onPlayerTick(PlayerTickEvent.Post event) {
		if (!event.getEntity().level().isClientSide() || event.getEntity() != Minecraft.getInstance().player) {
			return;
		}
		if (event.getEntity().tickCount % SAMPLE_INTERVAL_TICKS != 0) {
			return;
		}

		var player = event.getEntity();
		var level = player.level();
		String dimension = level.dimension().location().toString();
		dev.kubeui.gui.KubeUIExploredMapCache.markExplored(level, dimension, "surface", player.getBlockX(), player.getBlockZ(), 0);
	}

	@SubscribeEvent
	static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
		dev.kubeui.gui.KubeUIExploredMapCache.save();
	}
}
