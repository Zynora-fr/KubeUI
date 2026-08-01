package dev.kubeui.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Server-side registry of named actions a KubeUI screen can request via
/// `screen.runServerAction(id, data)`. Unlike [KubeUIContext#giveItem] (which just runs a
/// client-sent `/give` command, gated only by the player's own command permissions), an action
/// registered here runs entirely server-side - the handler decides what happens and whether it's
/// allowed, so this is safe to use for anything that must be trustworthy in normal survival
/// (a shop, a claim, anything with a cost). Bound as the `KubeUIActions` global in
/// `server_scripts`.
public final class KubeUIActions {
	private static final Map<String, KubeUIActionHandler> HANDLERS = new ConcurrentHashMap<>();

	private KubeUIActions() {
	}

	public static void register(String id, KubeUIActionHandler handler) {
		HANDLERS.put(id, handler);
	}

	static KubeUIActionHandler get(String id) {
		return HANDLERS.get(id);
	}
}
