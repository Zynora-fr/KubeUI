package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/// Shared helper for [KubeUIScreenBuilder#setFontScale(double)] - wraps a text draw call in a real
/// transform (`GuiGraphicsExtractor#pose`, same mechanism [KubeUIScreenBuilder#renderScale(double)]
/// uses) scaled around the text's own draw origin, so only the glyphs grow/shrink, not the widget's
/// box. Used by every KubeUI widget that draws its own text directly (richText, table, chart labels,
/// range slider, keybind capture, progress bar) - not `.label()`/`.button()`/etc., which are drawn
/// by vanilla's own widget classes KubeUI doesn't control the internals of.
final class KubeUIFontScale {
	private KubeUIFontScale() {
	}

	static void draw(GuiGraphicsExtractor graphics, float pivotX, float pivotY, Runnable text) {
		float scale = KubeUITheme.fontScale;
		if (scale == 1.0f) {
			text.run();
			return;
		}

		graphics.pose().pushMatrix();
		graphics.pose().scaleAround(scale, pivotX, pivotY);
		text.run();
		graphics.pose().popMatrix();
	}
}
