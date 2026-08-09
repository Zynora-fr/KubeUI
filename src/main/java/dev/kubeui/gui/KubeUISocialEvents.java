package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/// Opt-in friend-activity notifications (`KubeUIActions.setFriendActivityNotifications(player,
/// enabled)`) - "un ami se connecte" via the real toast system already delivered
/// ([KubeUIMachineAlerts]'s own server-push-a-toast mechanism, reused as-is). Same real
/// `PlayerEvent.PlayerLoggedInEvent` [KubeUISkillEvents] already uses for its own login hook.
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUISocialEvents {
	private static final java.util.Map<java.util.UUID, Boolean> NOTIFY_ENABLED = new java.util.concurrent.ConcurrentHashMap<>();

	private KubeUISocialEvents() {
	}

	static void setNotificationsEnabled(ServerPlayer player, boolean enabled) {
		NOTIFY_ENABLED.put(player.getUUID(), enabled);
	}

	@SubscribeEvent
	static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer joined)) {
			return;
		}
		var server = joined.level().getServer();
		if (server == null) {
			return;
		}
		for (var online : server.getPlayerList().getPlayers()) {
			if (online == joined || !NOTIFY_ENABLED.getOrDefault(online.getUUID(), false)) {
				continue;
			}
			if (KubeUIFriends.areFriends(online, joined.getUUID())) {
				KubeUIMachineAlerts.send(online, joined.getGameProfile().getName() + " came online");
			}
		}
	}
}
