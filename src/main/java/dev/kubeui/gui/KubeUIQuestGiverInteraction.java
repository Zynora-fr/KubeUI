package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/// Right-clicking any entity tagged via `KubeUIActions.tagQuestGiver(entity, questIds)` opens the
/// Quest Giver screen, listing every quest that entity was tagged with together with this
/// player's status on each. Same real `PlayerInteractEvent.EntityInteract` mechanism as
/// [KubeUIVillagerTradeInteraction] - checks `!event.isCanceled()` first so an entity that
/// happens to be tagged as *both* a trader and a quest giver doesn't open two screens from one
/// click (an unsupported combination either way, but a cheap, real guard against it costs
/// nothing).
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIQuestGiverInteraction {
	private KubeUIQuestGiverInteraction() {
	}

	@SubscribeEvent
	static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		if (event.isCanceled() || event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		var target = event.getTarget();
		if (!KubeUIQuests.isQuestGiver(target)) {
			return;
		}

		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
		sendGiverScreen(player, target);
	}

	/// Builds and sends the up-to-date Quest Giver payload for `giver` to `player` - called both
	/// on the initial right-click and again after an accept/complete action, so the screen always
	/// reflects the real, just-changed server state rather than the client guessing what changed.
	static void sendGiverScreen(ServerPlayer player, Entity giver) {
		var reply = new CompoundTag();
		reply.putString("giverUuid", giver.getUUID().toString());
		reply.putString("giverName", giver.getName().getString());

		var questsTag = new ListTag();
		for (var questId : KubeUIQuests.questGiverQuestIds(giver)) {
			var def = KubeUIQuests.get(questId);
			if (def == null) {
				continue;
			}
			questsTag.add(describeQuestForPlayer(player, def));
		}
		reply.put("quests", questsTag);

		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.QUEST_GIVER_RESULT_SCREEN_ID, reply));
	}

	/// Shared with [KubeUINetworking]'s `QUEST_LOG_REQUEST_ACTION` handler - a quest's status and
	/// progress are summarized the exact same way whether they're being shown in a quest-giver
	/// screen or the player's own quest log, so there's exactly one place that decides what
	/// `"locked"`/`"available"`/`"active"`/`"readyToComplete"`/`"completed"` means.
	static CompoundTag describeQuestForPlayer(ServerPlayer player, KubeUIQuestDef def) {
		var tag = new CompoundTag();
		tag.putString("id", def.id());
		tag.putString("title", def.title());
		tag.putString("description", def.description());

		String state = KubeUIQuests.state(player, def.id());
		String status = switch (state) {
			case "completed" -> "completed";
			case "active" -> KubeUIQuests.canComplete(player, def.id()) ? "readyToComplete" : "active";
			default -> KubeUIQuests.prerequisitesMet(player, def) ? "available" : "locked";
		};
		tag.putString("status", status);

		var objectivesTag = new ListTag();
		for (var objective : def.objectives()) {
			var objectiveTag = new CompoundTag();
			objectiveTag.putString("label", objective.label());
			objectiveTag.putInt("progress", "active".equals(state) || "completed".equals(state)
				? KubeUIQuests.objectiveProgress(player, def, objective) : 0);
			objectiveTag.putInt("target", objective.target());
			objectivesTag.add(objectiveTag);
		}
		tag.put("objectives", objectivesTag);

		var rewardsTag = new ListTag();
		for (var reward : def.rewards()) {
			rewardsTag.add(describeReward(reward));
		}
		tag.put("rewards", rewardsTag);

		return tag;
	}

	private static CompoundTag describeReward(KubeUIQuestReward reward) {
		var tag = new CompoundTag();
		tag.putString("type", reward.type());
		switch (reward.type()) {
			case "item" -> {
				tag.putString("item", KubeUINbtCompat.getStringOr(reward.data(), "item", ""));
				tag.putInt("count", KubeUINbtCompat.getIntOr(reward.data(), "count", 1));
			}
			case "xp" -> tag.putInt("levels", KubeUINbtCompat.getIntOr(reward.data(), "levels", 0));
			case "command" -> tag.putString("label", "Special reward");
			default -> {
			}
		}
		return tag;
	}
}
