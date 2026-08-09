package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/// `KubeUIChatScriptEvents.decorate(event => {...})` - a real `server_scripts` event, posted from
/// [dev.kubeui.plugin.KubeUIChatEvents] for every chat message before it's broadcast. See
/// [KubeUIChatDecorateEvent]'s own doc for why the guild-tag decoration itself lives entirely in a
/// script reacting to this, not in Java.
public interface KubeUIChatScriptEvents {
	EventGroup GROUP = EventGroup.of("KubeUIChatScriptEvents");

	EventHandler DECORATE = GROUP.server("decorate", () -> KubeUIChatDecorateEvent.class);
}
