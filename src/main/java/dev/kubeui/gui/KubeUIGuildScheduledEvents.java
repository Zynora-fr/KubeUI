package dev.kubeui.gui;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Scheduled guild events (`KubeUIActions.scheduleGuildEvent(guildId, eventId, name, delayTicks)`) -
/// a plain in-memory timer, not a persisted scheduler (an in-progress scheduled event lost to a
/// restart is an honest, accepted gap, the same "session-only" scope [KubeUIParties] already
/// accepts for its own temporary state). Fires [#TRIGGERED] once the timer elapses so a script can
/// distribute rewards through the *existing* real quest-reward system directly
/// (`KubeUIActions.grantSkillPoints`/`.pay`/a quest's own reward, whichever fits) rather than this
/// class reimplementing a second reward mechanism.
public final class KubeUIGuildScheduledEvents {
	record Scheduled(String guildId, String eventId, String name, long fireAtTick) {
	}

	public static final EventGroup GROUP = EventGroup.of("KubeUIGuildScriptEvents");
	public static final EventHandler TRIGGERED = GROUP.server("scheduledEventTriggered", () -> KubeUIGuildScheduledEvent.class);

	private static final Map<String, Scheduled> SCHEDULED = new ConcurrentHashMap<>();

	private KubeUIGuildScheduledEvents() {
	}

	static void schedule(MinecraftServer server, String guildId, String eventId, String name, long delayTicks) {
		SCHEDULED.put(guildId + ":" + eventId, new Scheduled(guildId, eventId, name, server.getTickCount() + Math.max(1, delayTicks)));
	}

	static void tick(MinecraftServer server, long tick) {
		if (SCHEDULED.isEmpty()) {
			return;
		}
		var due = SCHEDULED.values().stream().filter(s -> tick >= s.fireAtTick()).toList();
		for (var scheduled : due) {
			SCHEDULED.remove(scheduled.guildId() + ":" + scheduled.eventId());
			TRIGGERED.post(ScriptType.SERVER, new KubeUIGuildScheduledEvent(scheduled.guildId(), scheduled.eventId(), scheduled.name()));
		}
	}

	static List<Scheduled> upcoming(String guildId) {
		return SCHEDULED.values().stream().filter(s -> s.guildId().equals(guildId)).toList();
	}
}
