package dev.kubeui.gui;

import java.util.LinkedHashMap;
import java.util.Map;

/// Backs `KubeUI.registerCommand(name, callback)` - stores what scripts asked for, independent of
/// whether NeoForge has (re)built the client command dispatcher yet. Read by
/// `dev.kubeui.plugin.KubeUIScriptCommands` every time it does.
public final class KubeUICommandRegistry {
	private static final Map<String, Runnable> COMMANDS = new LinkedHashMap<>();

	private KubeUICommandRegistry() {
	}

	static void register(String name, Runnable callback) {
		COMMANDS.put(name, callback);
	}

	public static Map<String, Runnable> all() {
		return COMMANDS;
	}
}
