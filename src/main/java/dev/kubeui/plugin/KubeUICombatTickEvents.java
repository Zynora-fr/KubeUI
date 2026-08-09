package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUICombatLog;
import dev.kubeui.gui.KubeUICooldowns;
import dev.kubeui.gui.KubeUIStatusEffects;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/// One real per-player tick loop (`PlayerTickEvent.Post`, server-side checked) driving
/// [KubeUIStatusEffects#tick]/[KubeUICooldowns#tick]/[KubeUICombatLog#tick] - the same "share one
/// real tick loop rather than three separate subscribers" reasoning [KubeUIMachineBlockEntity]'s
/// own class doc already used for its batch.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUICombatTickEvents {
	private KubeUICombatTickEvents() {
	}

	@SubscribeEvent
	static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		KubeUIStatusEffects.tick(player);
		KubeUICooldowns.tick(player);
		KubeUICombatLog.tick(player);
	}
}
