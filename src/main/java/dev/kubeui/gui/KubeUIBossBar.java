package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Custom scriptable boss bar (`KubeUI.bossBar(...)`) - beyond vanilla `BossEvent`/`ServerBossEvent`
/// (real, but fixed color/style options and no concept of scriptable combat phases). Fully
/// KubeUI-drawn client-side (`KubeUIBossBarHudRenderer`), synced the same server-push way every
/// other HUD element in this mod already is (same convention as `KubeUIQuestHud`). Deliberately no
/// "current viewers" registry kept here - a script passes the viewer list to every call, same
/// explicit-audience shape `KubeUIActions.openRemote`/`.broadcastUpdate` already use, rather than
/// this class silently deciding who's "still watching" a fight.
final class KubeUIBossBar {
	record Phase(float threshold, int color, String text) {
	}

	private static final Map<String, List<Phase>> PHASES = new ConcurrentHashMap<>();

	private KubeUIBossBar() {
	}

	static void show(List<ServerPlayer> viewers, String barId, String name, float health, float maxHealth, List<Phase> phases) {
		PHASES.put(barId, phases);
		send(viewers, describe(barId, name, health, maxHealth, phases));
	}

	static void updateHealth(List<ServerPlayer> viewers, String barId, String name, float health, float maxHealth) {
		var phases = PHASES.getOrDefault(barId, List.of());
		send(viewers, describe(barId, name, health, maxHealth, phases));
	}

	static void hide(List<ServerPlayer> viewers, String barId) {
		PHASES.remove(barId);
		var data = new CompoundTag();
		data.putString("barId", barId);
		data.putBoolean("hide", true);
		send(viewers, data);
	}

	private static void send(List<ServerPlayer> viewers, CompoundTag data) {
		for (var player : viewers) {
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.BOSS_BAR_UPDATE_SCREEN_ID, data));
		}
	}

	/// Picks the *tightest* matching phase (smallest threshold still `>=` the current health
	/// fraction) rather than "whichever phase happens to be last in the script's own array" - a
	/// script can list phases in any order and this still resolves the same way.
	private static CompoundTag describe(String barId, String name, float health, float maxHealth, List<Phase> phases) {
		var data = new CompoundTag();
		data.putString("barId", barId);
		data.putString("name", name);
		data.putFloat("health", Math.max(0, health));
		data.putFloat("maxHealth", Math.max(1, maxHealth));

		float fraction = maxHealth > 0 ? Math.max(0, health) / maxHealth : 0;
		Phase best = null;
		for (var phase : phases) {
			if (fraction <= phase.threshold() && (best == null || phase.threshold() < best.threshold())) {
				best = phase;
			}
		}
		data.putInt("color", best != null ? best.color() : 0xFFCC3333);
		data.putString("phaseText", best != null ? best.text() : "");
		return data;
	}
}
