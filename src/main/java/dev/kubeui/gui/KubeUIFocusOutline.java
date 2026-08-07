package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;

/// Draws a visible focus outline for a KubeUI custom widget - vanilla `Button`/`Checkbox`/etc.
/// indicate keyboard focus through their own sprite theme, but `AbstractWidget` itself draws
/// nothing for it (confirmed against decompiled source - no shared focus-outline code above the
/// per-subclass `extractWidgetRenderState`), so a widget that implements its own rendering
/// entirely (as every custom KubeUI widget does) needs to draw one itself to have any Tab-focus
/// indication at all. Same color KubeUI already uses elsewhere for an "active/selected" state
/// (`KubeUIContextMenuWidget`'s border, a drag drop-target highlight).
final class KubeUIFocusOutline {
	private static final int COLOR = 0xFF45D6C9;

	private KubeUIFocusOutline() {
	}

	static void draw(GuiGraphicsExtractor graphics, AbstractWidget widget) {
		if (widget.isFocused()) {
			graphics.outline(widget.getX() - 1, widget.getY() - 1, widget.getWidth() + 2, widget.getHeight() + 2, COLOR);
		}
	}
}
