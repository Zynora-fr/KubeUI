package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/// Right-clicking any entity tagged via `KubeUIActions.tagDialogueNpc(entity, dialogueId)` opens
/// that dialogue at its root node (the concrete "click an NPC" trigger - see
/// [KubeUIDialogue]'s class docs for why this isn't the *only* way to start one). Same real
/// `PlayerInteractEvent.EntityInteract` mechanism, and the same "only if nothing else already
/// canceled this click" guard, as [KubeUIQuestGiverInteraction]/[KubeUIVillagerTradeInteraction].
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIDialogueInteraction {
	private KubeUIDialogueInteraction() {
	}

	@SubscribeEvent
	static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.isCanceled() || event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		var target = event.getTarget();
		String dialogueId = KubeUIDialogue.npcDialogueId(target);
		if (dialogueId.isEmpty()) {
			return;
		}

		var def = KubeUIDialogue.get(dialogueId);
		if (def == null) {
			return;
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);

		CompoundTag reply = KubeUIDialogue.resolveNode(player, dialogueId, def.rootNodeId(), target);
		if (reply != null) {
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.DIALOGUE_RESULT_SCREEN_ID, reply));
		}
	}
}
