package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.level.ServerPlayer;

/// `KubeUICombatEvents.statusTick(event => {...})` / `.combatEnded(event => {...})` -
/// `server_scripts` events, same real shape/registration convention [KubeUIEconomyEvents] already
/// established (KubeUI's first `server_scripts` event group). Registered via
/// [dev.kubeui.plugin.KubeUIServerPlugin#registerEvents].
public interface KubeUICombatEvents {
	EventGroup GROUP = EventGroup.of("KubeUICombatEvents");

	EventHandler STATUS_TICK = GROUP.server("statusTick", () -> KubeUIStatusTickEvent.class);
	EventHandler COMBAT_ENDED = GROUP.server("combatEnded", () -> KubeUICombatEndedEvent.class);

	static void postStatusTick(ServerPlayer player, String statusId, int remainingTicks) {
		STATUS_TICK.post(ScriptType.SERVER, new KubeUIStatusTickEvent(player, statusId, remainingTicks));
	}

	static void postCombatEnded(ServerPlayer player, float damageDealt, float damageTaken, long durationTicks, boolean victory) {
		COMBAT_ENDED.post(ScriptType.SERVER, new KubeUICombatEndedEvent(player, damageDealt, damageTaken, durationTicks, victory));
	}
}
