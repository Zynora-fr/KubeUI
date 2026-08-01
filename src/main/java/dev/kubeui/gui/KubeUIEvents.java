package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/// `KubeUIEvents.screenOpen(event => {...})` / `KubeUIEvents.screenClose(event => {...})` -
/// global events, fired for every KubeUI screen. Registered via
/// [KubeUIPlugin#registerEvents(dev.latvian.mods.kubejs.event.EventGroupRegistry)].
public interface KubeUIEvents {
	EventGroup GROUP = EventGroup.of("KubeUIEvents");

	EventHandler SCREEN_OPEN = GROUP.client("screenOpen", () -> KubeUIScreenEvent.class);
	EventHandler SCREEN_CLOSE = GROUP.client("screenClose", () -> KubeUIScreenEvent.class);
}
