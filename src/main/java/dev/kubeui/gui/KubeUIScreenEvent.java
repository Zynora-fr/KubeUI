package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.KubeEvent;

/// Fired by [KubeUIEvents#SCREEN_OPEN]/[KubeUIEvents#SCREEN_CLOSE] for *every* KubeUI screen,
/// regardless of which script opened it - unlike `.onOpen(...)`/`.onClose(...)` on the builder
/// itself (which only fire for that one screen), this is for addons/scripts that want to react to
/// any KubeUI screen appearing (e.g. a stats tracker, or another mod adjusting its own UI).
public class KubeUIScreenEvent implements KubeEvent {
	private final KubeUIContext screen;
	private final String title;

	KubeUIScreenEvent(KubeUIContext screen, String title) {
		this.screen = screen;
		this.title = title;
	}

	public KubeUIContext getScreen() {
		return screen;
	}

	public String getTitle() {
		return title;
	}
}
