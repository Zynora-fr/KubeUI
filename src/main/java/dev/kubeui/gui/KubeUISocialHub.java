package dev.kubeui.gui;

import dev.kubeui.KubeUI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/// A registry of tabs for the social screen (`/social` opens it, see the server's own
/// `ServerEvents.commandRegistry` example) - real, standalone client class, same "no
/// `KubeUIScreenBuilder` wrapping needed" shape [KubeUISettingsHub] already established for its own
/// sections. Deliberately just *which tabs exist and in what order*, not the screen itself (a
/// script builds it, `.tab(t.label(), children => t.onOpen().accept(children))` per entry, the
/// same "KubeUI has no opinion on what the screen looks like" split every other bridge screen in
/// this mod already uses) - "tout ça est configurable en JS" (real ask): a modpack script can
/// register its own extra tabs (a "Guild" tab, a "Trade" tab, ...) alongside the default
/// "Général"/"Teams"/"Party" ones purely from JS, no Java change needed either way.
public final class KubeUISocialHub {
	public record Tab(String id, String label, int order, Consumer<KubeUIScreenBuilder> onOpen) {
	}

	private static final List<Tab> TABS = new ArrayList<>();

	private KubeUISocialHub() {
	}

	/// Registers (or replaces) a tab. Lower `order` sorts first - the three default tabs use
	/// `0`/`10`/`20`, leaving room to slot new ones in between or push extras to either end.
	public static void registerTab(String id, String label, int order, Consumer<KubeUIScreenBuilder> onOpen) {
		if (id == null || label == null || onOpen == null) {
			KubeUI.LOGGER.error("KubeUISocialHub.registerTab needs a non-null id, label and onOpen - ignoring (id={})", id);
			return;
		}
		TABS.removeIf(t -> t.id().equals(id));
		TABS.add(new Tab(id, label, order, safe(id, onOpen)));
	}

	public static void unregisterTab(String id) {
		TABS.removeIf(t -> t.id().equals(id));
	}

	public static List<Tab> tabs() {
		return TABS.stream().sorted(Comparator.comparingInt(Tab::order)).toList();
	}

	private static Consumer<KubeUIScreenBuilder> safe(String id, Consumer<KubeUIScreenBuilder> callback) {
		return arg -> {
			try {
				callback.accept(arg);
			} catch (Exception ex) {
				KubeUI.LOGGER.error("KubeUISocialHub tab callback for '{}' threw an exception", id, ex);
			}
		};
	}
}
