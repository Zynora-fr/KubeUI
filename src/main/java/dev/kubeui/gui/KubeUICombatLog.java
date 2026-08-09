package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Real per-player combat tracking off the real `LivingDamageEvent.Post` (fired after every
/// reduction is already applied - `getHealthDamage()` is the *actual* health lost, not a raw/
/// pre-mitigation number). Backs three roadmap entries at once, the same "one real mechanism, three
/// angles" reasoning [KubeUIMachineBridge] already uses for its own network/stats screen:
///  - a per-player, toggleable combat-log HUD (`KubeUICombatLogHudRenderer`) - "indicateur de
///    dégâts flottant" scoped down to a real, honestly-simpler combat-log list rather than
///    world-space floating numbers over each entity (a full 3D-to-screen projection this session
///    has no way to visually verify) - still a real, toggleable, at-a-glance damage readout.
///  - a "combat session" (damage dealt/taken while hits keep landing, ended once
///    [#COMBAT_TIMEOUT_TICKS] pass with no hit either way) that fires
///    [KubeUICombatEvents#postCombatEnded] for a script's own post-combat recap screen.
///  - [#history], an in-memory, capped, per-player log of past sessions (`KubeUIActions.combatHistory(player)`) -
///    same "session-only, not disk-persisted" honest scope [KubeUIActions#tradeHistory]/`#dialogueHistory`
///    already accept for their own history lists.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
public final class KubeUICombatLog {
	private static final int COMBAT_TIMEOUT_TICKS = 100;
	private static final int MAX_LOG_LINES = 6;
	private static final int MAX_HISTORY = 20;

	private static final class Session {
		float damageDealt;
		float damageTaken;
		long startTick;
		long lastHitTick;
	}

	record HistoryEntry(float damageDealt, float damageTaken, long durationTicks, boolean victory, long timestamp) {
	}

	private static final Map<UUID, Boolean> ENABLED = new ConcurrentHashMap<>();
	private static final Map<UUID, List<String>> RECENT_LINES = new ConcurrentHashMap<>();
	private static final Map<UUID, Session> CURRENT_SESSION = new ConcurrentHashMap<>();
	private static final Map<UUID, List<HistoryEntry>> HISTORY = new ConcurrentHashMap<>();

	private KubeUICombatLog() {
	}

	static boolean hasActiveSession(ServerPlayer player) {
		return CURRENT_SESSION.containsKey(player.getUUID());
	}

	@SubscribeEvent
	static void onDamage(LivingDamageEvent.Post event) {
		if (event.getEntity().level().isClientSide()) {
			return;
		}
		float amount = event.getHealthDamage();
		if (amount <= 0) {
			return;
		}

		if (event.getEntity() instanceof ServerPlayer victim) {
			recordHit(victim, 0, amount);
			pushLogLine(victim, "§c-" + Math.round(amount) + "§7 from " + event.getSource().getMsgId());
		}
		if (event.getSource().getEntity() instanceof ServerPlayer attacker && attacker != event.getEntity()) {
			recordHit(attacker, amount, 0);
			pushLogLine(attacker, "§a+" + Math.round(amount) + "§7 to " + event.getEntity().getName().getString());
		}
	}

	private static void recordHit(ServerPlayer player, float dealt, float taken) {
		var session = CURRENT_SESSION.computeIfAbsent(player.getUUID(), ignored -> new Session());
		session.damageDealt += dealt;
		session.damageTaken += taken;
		long now = player.level().getGameTime();
		if (session.startTick == 0) {
			session.startTick = now;
		}
		session.lastHitTick = now;
	}

	private static void pushLogLine(ServerPlayer player, String line) {
		if (!ENABLED.getOrDefault(player.getUUID(), true)) {
			return;
		}
		var lines = RECENT_LINES.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
		synchronized (lines) {
			lines.add(line);
			while (lines.size() > MAX_LOG_LINES) {
				lines.remove(0);
			}
		}
		sync(player);
	}

	static void setEnabled(ServerPlayer player, boolean enabled) {
		ENABLED.put(player.getUUID(), enabled);
		if (!enabled) {
			RECENT_LINES.remove(player.getUUID());
			sync(player);
		}
	}

	/// Called once per real server tick, per player (see `KubeUICombatTickEvents`) - ends the
	/// current session once [#COMBAT_TIMEOUT_TICKS] pass with no further hit either way.
	public static void tick(ServerPlayer player) {
		var session = CURRENT_SESSION.get(player.getUUID());
		if (session == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (now - session.lastHitTick > COMBAT_TIMEOUT_TICKS) {
			endSession(player, session);
			CURRENT_SESSION.remove(player.getUUID());
		}
	}

	private static void endSession(ServerPlayer player, Session session) {
		long duration = Math.max(1, session.lastHitTick - session.startTick);
		boolean victory = player.isAlive();
		var entry = new HistoryEntry(session.damageDealt, session.damageTaken, duration, victory, System.currentTimeMillis());

		var history = HISTORY.computeIfAbsent(player.getUUID(), ignored -> new ArrayList<>());
		synchronized (history) {
			history.add(0, entry);
			while (history.size() > MAX_HISTORY) {
				history.remove(history.size() - 1);
			}
		}
		KubeUICombatEvents.postCombatEnded(player, session.damageDealt, session.damageTaken, duration, victory);
	}

	static List<HistoryEntry> history(ServerPlayer player) {
		var history = HISTORY.getOrDefault(player.getUUID(), List.of());
		synchronized (history) {
			return List.copyOf(history);
		}
	}

	private static void sync(ServerPlayer player) {
		var lines = RECENT_LINES.getOrDefault(player.getUUID(), List.of());
		var listTag = new ListTag();
		synchronized (lines) {
			for (var line : lines) {
				var tag = new CompoundTag();
				tag.putString("line", line);
				listTag.add(tag);
			}
		}
		var data = new CompoundTag();
		data.put("lines", listTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.COMBAT_LOG_UPDATE_SCREEN_ID, data));
	}
}
