package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.level.ServerPlayer;

/// `KubeUIEconomyEvents.transaction(event => {...})` / `.balanceChanged(event => {...})` -
/// `server_scripts` events (unlike [KubeUIEvents], which is client-only), posted from
/// [KubeUICurrency] on every [KubeUICurrency#pay]/[#charge]/[#transfer]. Registered via
/// [dev.kubeui.plugin.KubeUIServerPlugin#registerEvents], the server-side counterpart of how
/// [KubeUIEvents#GROUP] gets registered client-side.
public interface KubeUIEconomyEvents {
	EventGroup GROUP = EventGroup.of("KubeUIEconomyEvents");

	EventHandler TRANSACTION = GROUP.server("transaction", () -> KubeUIEconomyEvent.class);
	EventHandler BALANCE_CHANGED = GROUP.server("balanceChanged", () -> KubeUIEconomyEvent.class);

	static void postTransaction(ServerPlayer player, String currency, long delta, long balance, String type) {
		var event = new KubeUIEconomyEvent(player, currency, delta, balance, type);
		TRANSACTION.post(ScriptType.SERVER, event);
		BALANCE_CHANGED.post(ScriptType.SERVER, event);
	}
}
