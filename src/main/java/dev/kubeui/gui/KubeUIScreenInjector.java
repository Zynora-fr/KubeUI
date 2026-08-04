package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.gui.screens.Screen;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/// Generalizes the sidebar's injection mechanism (`KubeUISidebarInjector`, specific to
/// `InventoryScreen`) to *any* vanilla or third-party `Screen` subclass:
/// `KubeUIScreenInjector.register('net.minecraft.client.gui.screens.inventory.InventoryScreen', (screen, panel) => panel.button(...))`.
/// `screenClassName` is a fully-qualified class name (a plain string, not a `Class` reference a
/// script has no easy way to obtain) - resolved once, at registration time, via `Class.forName`.
/// The actual injection (`ScreenEvent.Init.Post` + `Screen#addRenderableWidget`) lives in
/// `dev.kubeui.plugin.KubeUIScreenInjectorHandler`, which reads this registry.
public final class KubeUIScreenInjector {
	private static final Map<Class<?>, BiConsumer<Screen, KubeUIScreenBuilder>> REGISTRY = new HashMap<>();

	private KubeUIScreenInjector() {
	}

	/// `configure(screen, panel)` is called once every time a screen of this class opens - `screen`
	/// is that real instance (read its position/size to place your panel sensibly, e.g. the same
	/// `containerScreen.getLeftPos()` trick the sidebar itself uses); `panel` is an empty builder to
	/// populate like any other (`.button(...)`, `.label(...)`, ...) - its own title/tabs/draggable/
	/// resizable settings are ignored, only its top-level elements are injected.
	public static void register(String screenClassName, BiConsumer<Screen, KubeUIScreenBuilder> configure) {
		try {
			Class<?> clazz = Class.forName(screenClassName);
			if (!Screen.class.isAssignableFrom(clazz)) {
				KubeUI.LOGGER.error("KubeUI: registerScreenInjector('{}', ...) - not a Screen subclass", screenClassName);
				return;
			}
			REGISTRY.put(clazz, safe(configure));
		} catch (ClassNotFoundException e) {
			KubeUI.LOGGER.error("KubeUI: registerScreenInjector('{}', ...) - class not found (typo, or that mod isn't loaded)", screenClassName);
		}
	}

	public static BiConsumer<Screen, KubeUIScreenBuilder> forScreen(Screen screen) {
		return REGISTRY.get(screen.getClass());
	}

	private static BiConsumer<Screen, KubeUIScreenBuilder> safe(BiConsumer<Screen, KubeUIScreenBuilder> configure) {
		return (screen, panel) -> {
			try {
				configure.accept(screen, panel);
			} catch (Exception e) {
				KubeUI.LOGGER.error("KubeUI: error in a registerScreenInjector(...) callback for {}", screen.getClass().getName(), e);
			}
		};
	}
}
