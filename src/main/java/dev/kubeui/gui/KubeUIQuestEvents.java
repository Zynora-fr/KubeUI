package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/// Everything about the quest system that needs a real Minecraft/NeoForge event rather than a
/// direct method call: loading quest-editor quests from disk when a world starts, incrementing
/// `"kill"` objective counters when a tracked entity type dies at a player's hand, periodically
/// checking `"visit"` objectives and pushing HUD progress for whichever quest a player is
/// currently tracking, and clearing that (session-only) tracking on logout.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIQuestEvents {
	/// Once per second (20 ticks) per player is plenty for a `"visit"` check (a player can't
	/// meaningfully enter/leave a region faster than that matters) and for a HUD refresh (progress
	/// numbers that lag by up to a second are unnoticeable, and this keeps a long-running server
	/// with many tracked quests cheap).
	private static final int CHECK_INTERVAL_TICKS = 20;

	private KubeUIQuestEvents() {
	}

	@SubscribeEvent
	static void onServerAboutToStart(ServerAboutToStartEvent event) {
		KubeUIQuests.loadEditorQuests(event.getServer());
	}

	@SubscribeEvent
	static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		KubeUIQuests.clearTrackedQuest(event.getEntity().getUUID());
	}

	/// Kill-credit uses `DamageSource#getEntity()` (the real attacker - e.g. the player who shot an
	/// arrow, not the arrow itself), the same source vanilla's own advancement triggers use, not
	/// `getDirectEntity()`.
	@SubscribeEvent
	static void onLivingDeath(LivingDeathEvent event) {
		if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
			return;
		}

		var deadType = event.getEntity().getType();
		for (var def : KubeUIQuests.all()) {
			if (!"active".equals(KubeUIQuests.state(killer, def.id()))) {
				continue;
			}
			for (var objective : def.objectives()) {
				if (!"kill".equals(objective.type())) {
					continue;
				}
				var wantedType = KubeUIQuests.resolveEntityType(objective.data().getStringOr("entity", ""));
				if (wantedType != null && wantedType == deadType) {
					KubeUIQuests.incrementObjectiveCounter(killer, def.id(), objective.id(), 1);
				}
			}
		}
	}

	@SubscribeEvent
	static void onPlayerTick(PlayerTickEvent.Post event) {
		if (event.getEntity().level().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (player.tickCount % CHECK_INTERVAL_TICKS != 0) {
			return;
		}

		checkVisitObjectives(player);
		pushHudUpdate(player);
	}

	private static void checkVisitObjectives(ServerPlayer player) {
		for (var def : KubeUIQuests.all()) {
			if (!"active".equals(KubeUIQuests.state(player, def.id()))) {
				continue;
			}
			for (var objective : def.objectives()) {
				if (!"visit".equals(objective.type()) || KubeUIQuests.isVisited(player, def.id(), objective.id())) {
					continue;
				}
				if (KubeUIQuests.checkVisit(player, objective)) {
					KubeUIQuests.markVisited(player, def.id(), objective.id());
				}
			}
		}
	}

	/// Package-visible so [KubeUINetworking]'s `QUEST_TRACK_ACTION` handler can push one
	/// immediately when a player starts tracking a quest, instead of leaving them looking at an
	/// empty HUD for up to a second until the next throttled tick check fires.
	static void pushHudUpdate(ServerPlayer player) {
		String questId = KubeUIQuests.trackedQuest(player);
		if (questId == null) {
			return;
		}

		var def = KubeUIQuests.get(questId);
		var reply = new CompoundTag();
		if (def == null || !"active".equals(KubeUIQuests.state(player, questId))) {
			// The tracked quest was deleted, or completed/abandoned since it was last tracked -
			// stop tracking it server-side and tell the client to hide the overlay, rather than
			// silently going quiet (which would leave a stale, no-longer-true progress number
			// on screen forever).
			KubeUIQuests.setTrackedQuest(player, null);
			reply.putString("questId", "");
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.QUEST_HUD_UPDATE_SCREEN_ID, reply));
			return;
		}

		reply.putString("questId", def.id());
		reply.putString("title", def.title());
		var objectivesTag = new net.minecraft.nbt.ListTag();
		for (var objective : def.objectives()) {
			var objectiveTag = new CompoundTag();
			objectiveTag.putString("label", objective.label());
			objectiveTag.putInt("progress", KubeUIQuests.objectiveProgress(player, def, objective));
			objectiveTag.putInt("target", objective.target());
			objectivesTag.add(objectiveTag);
		}
		reply.put("objectives", objectivesTag);
		reply.putBoolean("canComplete", KubeUIQuests.canComplete(player, questId));
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.QUEST_HUD_UPDATE_SCREEN_ID, reply));
	}
}
