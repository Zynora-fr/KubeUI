package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/// A small colored status pill (`.badge(text, color)`), e.g. "New"/"In stock"/"Sold
/// out". Not interactive - purely decorative, sized to fit its text.
class KubeUIBadge extends AbstractWidget implements KubeUINarratable {
	private static final int PADDING_X = 6;

	private final Font font;
	private final int color;
	private String narration;

	KubeUIBadge(int x, int y, String text, int color, Font font) {
		super(x, y, font.width(text) + PADDING_X * 2, font.lineHeight + 4, Component.literal(text));
		this.font = font;
		this.color = color;
		this.active = false;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void renderWidget(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		extractWidgetRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), color);

		int textColor = KubeUILayoutMath.readableTextColor(color);
		graphics.text(font, getMessage(), getX() + PADDING_X, getY() + (getHeight() - font.lineHeight) / 2 + 1, textColor, false);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, narration != null ? Component.literal(narration) : getMessage());
	}
}
