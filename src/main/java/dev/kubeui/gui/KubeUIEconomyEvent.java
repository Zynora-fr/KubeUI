package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/// Fired by [KubeUIEconomyEvents#TRANSACTION]/[#BALANCE_CHANGED] whenever [KubeUICurrency] mutates
/// a balance - `type` is `"pay"` or `"charge"` (a [KubeUICurrency#transfer] posts one of each: a
/// `"charge"` on the sender, a `"pay"` on the recipient, exactly like two independent calls would,
/// since that's really what a transfer is under the hood). Both events carry identical data -
/// `.transaction(...)` is for a script that only cares about the mutation itself (a log, an
/// anti-cheat check), `.balanceChanged(...)` for one that cares about reacting to the *resulting*
/// balance (a quest objective, a scoreboard) - same split in spirit as `KubeUIQuestEvents`'s
/// separate kill/visit hooks versus a single generic "quest changed" event.
public class KubeUIEconomyEvent implements KubeEvent {
	private final ServerPlayer player;
	private final String currency;
	private final long delta;
	private final long balance;
	private final String type;

	KubeUIEconomyEvent(ServerPlayer player, String currency, long delta, long balance, String type) {
		this.player = player;
		this.currency = currency;
		this.delta = delta;
		this.balance = balance;
		this.type = type;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	public String getCurrency() {
		return currency;
	}

	/// Positive for a `"pay"`, negative for a `"charge"`.
	public long getDelta() {
		return delta;
	}

	public long getBalance() {
		return balance;
	}

	public String getType() {
		return type;
	}
}
