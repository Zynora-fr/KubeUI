package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.KubeEvent;

/// Fired by [KubeUIGuildScheduledEvents#TRIGGERED] once a `KubeUIActions.scheduleGuildEvent(...)`
/// timer elapses.
public class KubeUIGuildScheduledEvent implements KubeEvent {
	private final String guildId;
	private final String eventId;
	private final String name;

	KubeUIGuildScheduledEvent(String guildId, String eventId, String name) {
		this.guildId = guildId;
		this.eventId = eventId;
		this.name = name;
	}

	public String getGuildId() {
		return guildId;
	}

	public String getEventId() {
		return eventId;
	}

	public String getName() {
		return name;
	}
}
