package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.VersionChecker;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/// Tells the player, in chat, whether KubeUI is up to date whenever they join a world - built
/// entirely on NeoForge's own update-checker (`updateJSONURL` in `neoforge.mods.toml`, resolved
/// via curseupdate.com - which maps a CurseForge project to the same JSON format Forge/NeoForge's
/// checker expects, without needing a CurseForge Core API key). [VersionChecker] already fetches
/// and caches the result once per game launch (started automatically by NeoForge itself during
/// common setup, well before any world is joined) - this class only reads that cached result and
/// turns it into a chat message; it makes no network calls of its own.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIUpdateChecker {
	// The version check almost always finished well before a player gets around to joining a
	// world, but isn't guaranteed to have - this bounds how long we keep polling the (already
	// cached, so cheap) result each tick before giving up silently.
	private static final int MAX_WAIT_TICKS = 200;

	private static boolean waitingForResult = false;
	private static int waitedTicks = 0;

	private KubeUIUpdateChecker() {
	}

	@SubscribeEvent
	static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
		waitedTicks = 0;
		waitingForResult = true;
	}

	@SubscribeEvent
	static void onTick(ClientTickEvent.Post event) {
		if (!waitingForResult) {
			return;
		}

		var mod = ModList.get().getModContainerById(KubeUI.MOD_ID);
		if (mod.isEmpty()) {
			waitingForResult = false;
			return;
		}

		var result = VersionChecker.getResult(mod.get().getModInfo());
		if (result.status() == VersionChecker.Status.PENDING && ++waitedTicks < MAX_WAIT_TICKS) {
			return;
		}

		waitingForResult = false;
		announce(result, mod.get().getModInfo().getVersion().toString());
	}

	private static void announce(VersionChecker.CheckResult result, String currentVersion) {
		var player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}

		switch (result.status()) {
			case UP_TO_DATE -> player.sendSystemMessage(
				Component.literal("[KubeUI] You're running the latest version (" + currentVersion + ").")
					.withStyle(ChatFormatting.GREEN)
			);
			case OUTDATED, BETA_OUTDATED -> {
				String target = result.target() != null ? result.target().toString() : "a newer version";
				String message = "[KubeUI] Update available: " + target + " (you have " + currentVersion + ")"
					+ (result.url() != null ? " - " + result.url() : "");
				player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.YELLOW));
			}
			case FAILED -> KubeUI.LOGGER.warn("KubeUI update check failed - see NeoForge's own log line above for the cause");
			// AHEAD (a dev/newer-than-recommended build) and BETA (no recommended promo published)
			// aren't actionable for the player - nothing to tell them. PENDING here means we gave
			// up waiting, not that it's actually still pending forever.
			default -> {
			}
		}
	}
}
