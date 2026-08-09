package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/// The "XP/level" auto point grant, and reapplying already-unlocked nodes' real
/// `AttributeModifier`s on login (see [KubeUISkills#reapplyAll]'s own doc for why that's safe to
/// do unconditionally). Quest-triggered points (the third source) don't need a
/// hook here at all - a quest's own `command`-type reward (already real, already exists) can
/// already call `KubeUIActions.grantSkillPoints(...)` directly, same as any other server script
/// reacting to any other event.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUISkillEvents {
	private KubeUISkillEvents() {
	}

	@SubscribeEvent
	static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			KubeUISkills.reapplyAll(player);
		}
	}

	@SubscribeEvent
	static void onLevelChange(PlayerXpEvent.LevelChange event) {
		if (event.getLevels() <= 0 || !(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		for (var def : KubeUISkills.all()) {
			if (def.pointsPerLevel() > 0) {
				KubeUISkills.grantPoints(player, def.id(), def.pointsPerLevel() * event.getLevels());
			}
		}
	}
}
