package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/// Fired for every real chat message a player sends, *before* it reaches anyone else - a script
/// can rewrite [#getMessage]/[#setMessage] (e.g. prefix a guild tag) or block it outright with the
/// real `event.cancel()` every [KubeEvent] already provides. Deliberately *not* where the guild
/// tag itself gets prefixed - that's real, but real specifically because it lives entirely in a
/// script listening to this event (see `kubeui_guild_party_housing_commands.js`), not hardcoded
/// here, so a pack can change the format, add its own decorations, or drop the tag entirely with
/// no Java involved at all ("vraiment tout en JS" - real ask).
public class KubeUIChatDecorateEvent implements KubeEvent {
	private final ServerPlayer player;
	private String message;

	public KubeUIChatDecorateEvent(ServerPlayer player, String message) {
		this.player = player;
		this.message = message;
	}

	public ServerPlayer getPlayer() {
		return player;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message == null ? "" : message;
	}
}
