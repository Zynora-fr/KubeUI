package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Script-facing registry for a Paladium-style icon bar next to the survival inventory screen -
/// exposed to KubeJS as the global `KubeUISidebar` binding (client scripts only). Icons are
/// rendered/injected by [KubeUISidebarInjector]; this class only holds the registry and the
/// safety wrapping around each callback (same "a script exception never crashes the client"
/// policy as every other KubeUI callback - see [KubeUIScreenBuilder]).
///
/// Example (KubeJS client script):
/// ```js
/// KubeUISidebar.addItem('myaddon:shop', 'minecraft:emerald', 'Open the shop', () => openShop())
/// KubeUISidebar.addTexture('myaddon:quests', 'myaddon:textures/gui/quests_icon.png', 'Quests', () => openQuests())
/// ```
public final class KubeUISidebar {
	/// Pixel size (square) of each icon button - needed by `KubeUISidebarInjector` (a different
	/// package) to position icons relative to the inventory panel before any are actually built.
	public static final int ICON_SIZE = KubeUISidebarWidget.SIZE;

	/// Insertion order = display order, top to bottom.
	private static final Map<String, KubeUISidebarIcon> ICONS = new LinkedHashMap<>();

	/// Per-icon-id texture overrides (see [#setIconPack]) - checked before an icon's own
	/// item/texture, so a resource pack (or a script standing in for one) can reskin the whole bar
	/// in one call without every addon that registered an icon needing to cooperate.
	private static final Map<String, Identifier> ICON_PACK = new HashMap<>();

	/// Icon ids the server explicitly hid for this player (see [#setServerVisible]) - absence means
	/// visible, the same "opt-in to hide, not opt-in to show" default `.requirePermission(...)`
	/// widgets deliberately do the *opposite* of (those start hidden/disabled until confirmed,
	/// since a widget is a potential action; a sidebar icon merely opening a menu is lower-stakes
	/// and a server that never bothers calling this shouldn't have every icon vanish by default).
	private static final java.util.Set<String> SERVER_HIDDEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private KubeUISidebar() {
	}

	/// Overrides the rendered icon for every id present in `overrides` (icon id -> replacement
	/// texture), regardless of whether that icon was originally registered via [#addItem] or
	/// [#addTexture] - for reskinning the whole bar to match a resource pack in one call, without
	/// every addon that registered an icon needing to cooperate. An id with no override keeps
	/// rendering whatever it was registered with. Replaces any previous pack entirely (not merged).
	public static void setIconPack(Map<String, Identifier> overrides) {
		ICON_PACK.clear();
		if (overrides != null) {
			ICON_PACK.putAll(overrides);
		}
	}

	/// Undoes [#setIconPack] - every icon goes back to rendering its own registered item/texture.
	public static void clearIconPack() {
		ICON_PACK.clear();
	}

	/// Adds (or replaces, keeping its original position) an icon rendered as a vanilla item, like
	/// an inventory slot. `tooltip` may be null/empty for no tooltip.
	public static void addItem(String id, Item item, String tooltip, Runnable onClick) {
		if (id == null || item == null || onClick == null) {
			KubeUI.LOGGER.error("KubeUISidebar.addItem needs a non-null id, item and onClick - ignoring (id={})", id);
			return;
		}

		ICONS.put(id, new KubeUISidebarIcon(id, item, null, tooltip, safe(id, onClick)));
	}

	/// Same as [#addItem], but rendered from a custom resource-pack texture instead of an item.
	public static void addTexture(String id, Identifier texture, String tooltip, Runnable onClick) {
		if (id == null || texture == null || onClick == null) {
			KubeUI.LOGGER.error("KubeUISidebar.addTexture needs a non-null id, texture and onClick - ignoring (id={})", id);
			return;
		}

		ICONS.put(id, new KubeUISidebarIcon(id, null, texture, tooltip, safe(id, onClick)));
	}

	/// Removes a previously-added icon. Does nothing if `id` isn't registered.
	public static void remove(String id) {
		ICONS.remove(id);
	}

	/// Removes every registered icon.
	public static void clear() {
		ICONS.clear();
	}

	/// Called (via `KubeUINetworking`) when the server updates this player's visibility for
	/// `iconId` - see `KubeUIActions.setSidebarIconVisible`. Not script-facing directly.
	static void setServerVisible(String iconId, boolean visible) {
		if (iconId == null) {
			return;
		}
		if (visible) {
			SERVER_HIDDEN.remove(iconId);
		} else {
			SERVER_HIDDEN.add(iconId);
		}
	}

	/// Builds the actual clickable widgets, stacked vertically starting at `(x, y)` with `gap`
	/// pixels between them - called by `KubeUISidebarInjector` (a different package, hence
	/// `AbstractWidget`/`Minecraft` rather than the package-private [KubeUISidebarWidget]/
	/// [KubeUISidebarIcon] types directly).
	public static List<AbstractWidget> createIconWidgets(int x, int y, int gap) {
		var font = Minecraft.getInstance().font;
		var widgets = new ArrayList<AbstractWidget>();
		int currentY = y;

		for (var icon : ICONS.values()) {
			if (SERVER_HIDDEN.contains(icon.id())) {
				continue;
			}
			widgets.add(new KubeUISidebarWidget(x, currentY, icon, font, ICON_PACK.get(icon.id())));
			currentY += KubeUISidebarWidget.SIZE + gap;
		}

		return widgets;
	}

	private static Runnable safe(String id, Runnable callback) {
		return () -> {
			try {
				callback.run();
			} catch (Exception ex) {
				KubeUI.LOGGER.error("KubeUISidebar icon '{}' threw an exception", id, ex);
			}
		};
	}
}
