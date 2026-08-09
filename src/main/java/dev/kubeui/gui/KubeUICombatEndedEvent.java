package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/// Fired by [KubeUICombatEvents#COMBAT_ENDED] once [KubeUICombatLog] decides a player's tracked
/// fight is over (see its own doc for the real "no hit for N ticks" end condition) - a script can
/// react to the summary directly (open a custom post-fight screen, award a reward) instead of only
/// ever reading it back later via `KubeUIActions.combatHistory(player)`.
public class KubeUICombatEndedEvent implements KubeEvent {
	private final ServerPlayer player;
	private final float damageDealt;
	private final float damageTaken;
	private final long durationTicks;
	private final boolean victory;

	KubeUICombatEndedEvent(ServerPlayer player, float damageDealt, float damageTaken, long durationTicks, boolean victory) {
		this.player = player;
		this.damageDealt = damageDealt;
		this.damageTaken = damageTaken;
		this.durationTicks = durationTicks;
		this.victory = victory;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	public float getDamageDealt() {
		return damageDealt;
	}

	public float getDamageTaken() {
		return damageTaken;
	}

	public long getDurationTicks() {
		return durationTicks;
	}

	/// `true` if the player survived (didn't die during the tracked session) - the closest a
	/// generic, opponent-agnostic combat tracker can get to "won", since it has no concept of a
	/// specific boss/objective to check completion against.
	public boolean isVictory() {
		return victory;
	}
}
