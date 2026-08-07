package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/// Client-side registry a `client_scripts` file uses to opt in to server-pushed screens
/// (`KubeUIActions.openRemote(player, screenId, data)` / `.broadcastUpdate(screenId, data)` on the
/// server, see [KubeUIActions]) - register once at script load, matching the id a server script
/// will send. The callback is responsible for actually building and `.open()`-ing a screen (or
/// updating an already-open one via `screen.update(...)`) from `data` - KubeUI itself has no
/// opinion on what that screen looks like.
public final class KubeUIRemoteScreens {
	private static final Map<String, Consumer<CompoundTag>> HANDLERS = new ConcurrentHashMap<>();

	private KubeUIRemoteScreens() {
	}

	/// Registers (or replaces) the handler for `screenId`. `onReceive` is called with whatever
	/// `data` the server sent, both when the server explicitly opens this screen for a player and
	/// when it broadcasts an update to one already showing.
	public static void register(String screenId, Consumer<CompoundTag> onReceive) {
		if (screenId == null || onReceive == null) {
			KubeUI.LOGGER.error("KubeUIRemoteScreens.register needs a non-null screenId and onReceive - ignoring (screenId={})", screenId);
			return;
		}

		HANDLERS.put(screenId, data -> {
			try {
				onReceive.accept(data);
			} catch (Exception ex) {
				KubeUI.LOGGER.error("KubeUIRemoteScreens handler for '{}' threw an exception", screenId, ex);
			}
		});
	}

	/// Removes a previously registered handler. No-op if `screenId` isn't registered.
	public static void unregister(String screenId) {
		HANDLERS.remove(screenId);
	}

	/// Called by [KubeUINetworking] when a [KubeUIRemotePayload] arrives. Logs (doesn't throw) if
	/// nothing is registered for `screenId` - a server pushing to a client running an older/
	/// differently-configured script pack shouldn't crash it.
	static void receive(String screenId, CompoundTag data) {
		var handler = HANDLERS.get(screenId);
		if (handler != null) {
			handler.accept(data);
		} else {
			KubeUI.LOGGER.warn("Received KubeUI remote screen '{}' with no matching KubeUIRemoteScreens.register(...) on this client", screenId);
		}
	}
}
