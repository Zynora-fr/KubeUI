package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/// Right-clicking any entity tagged via `KubeUIActions.tagTradePool(entity, poolId)` opens the
/// real merchant-style trading GUI ([KubeUITradeExecuteMenu]) instead of that entity's normal
/// interaction - real NeoForge event (`PlayerInteractEvent.EntityInteract`, verified against the
/// real decompiled class), not a KubeJS scripting hook (KubeJS's own `EntityEvents` has no
/// interact/right-click event at all in this version - confirmed by decompiling it - so this had
/// to be done directly in KubeUI's own Java rather than left for a script to hook).
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIVillagerTradeInteraction {
	private KubeUIVillagerTradeInteraction() {
	}

	@SubscribeEvent
	static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		var target = event.getTarget();
		if (!KubeUIVillagerTrades.hasPool(target)) {
			return;
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		KubeUITradeExecuteMenu.open(player, target);
	}
}
