package dev.kubeui.gui;

import net.minecraft.client.gui.Font;

/// Passed to a [KubeUIWidgetFactory] when its widget type is used from a script via
/// `.custom("type", ...args)`.
public final class KubeUIWidgetFactoryContext {
	public final Font font;
	public final KubeUIContext screen;
	public final int width;
	public final int height;
	public final Object[] args;

	KubeUIWidgetFactoryContext(Font font, KubeUIContext screen, int width, int height, Object[] args) {
		this.font = font;
		this.screen = screen;
		this.width = width;
		this.height = height;
		this.args = args;
	}
}
