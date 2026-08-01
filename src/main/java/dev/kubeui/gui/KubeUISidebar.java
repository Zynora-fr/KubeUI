package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
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

	private KubeUISidebar() {
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

	/// Builds the actual clickable widgets, stacked vertically starting at `(x, y)` with `gap`
	/// pixels between them - called by `KubeUISidebarInjector` (a different package, hence
	/// `AbstractWidget`/`Minecraft` rather than the package-private [KubeUISidebarWidget]/
	/// [KubeUISidebarIcon] types directly).
	public static List<AbstractWidget> createIconWidgets(int x, int y, int gap) {
		var font = Minecraft.getInstance().font;
		var widgets = new ArrayList<AbstractWidget>();
		int currentY = y;

		for (var icon : ICONS.values()) {
			widgets.add(new KubeUISidebarWidget(x, currentY, icon, font));
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
