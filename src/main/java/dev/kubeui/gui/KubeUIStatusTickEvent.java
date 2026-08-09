package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/// Fired by [KubeUICombatEvents#STATUS_TICK] once per real server tick for every status a script
/// applied via `KubeUIActions.applyStatus(...)` (see [KubeUIStatusEffects]) that's still active on
/// `player` - the status's entire "own tick logic" (damage-over-time, a stacking counter, ...) since
/// a script-defined status has no built-in `MobEffect`-shaped behavior of its own.
public class KubeUIStatusTickEvent implements KubeEvent {
	private final ServerPlayer player;
	private final String statusId;
	private final int remainingTicks;

	KubeUIStatusTickEvent(ServerPlayer player, String statusId, int remainingTicks) {
		this.player = player;
		this.statusId = statusId;
		this.remainingTicks = remainingTicks;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	public String getStatusId() {
		return statusId;
	}

	/// `-1` means the status was applied with an infinite duration.
	public int getRemainingTicks() {
		return remainingTicks;
	}
}
