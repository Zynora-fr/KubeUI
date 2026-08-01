package dev.kubeui.gui;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Registry of third-party widget types, added by **Java** mods (not scripts) - see
/// [KubeUIWidgetFactory]. A typical mod would call [#register] once from its own client init.
public final class KubeUIWidgets {
	private static final Map<String, KubeUIWidgetFactory> REGISTRY = new ConcurrentHashMap<>();

	private KubeUIWidgets() {
	}

	public static void register(String type, KubeUIWidgetFactory factory) {
		REGISTRY.put(type, factory);
	}

	static KubeUIWidgetFactory get(String type) {
		return REGISTRY.get(type);
	}
}
