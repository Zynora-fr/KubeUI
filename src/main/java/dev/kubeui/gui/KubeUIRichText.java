package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/// Multi-style text on one wrapped block (`.richText(id, component)`), unlike
/// `.label()` which only takes a single flat string/style. Scripts build the styling themselves
/// with vanilla `Component`/`Style` (`.withBold(true)`, `.withColor(...)`, `.append(...)` for
/// multiple runs) - KubeUI just lays the result out and wraps it, it doesn't invent its own
/// styling API for this.
class KubeUIRichText extends AbstractWidget implements KubeUINarratable {
	private final DoubleClickTracker doubleClickTracker = new DoubleClickTracker();
	private final Font font;
	private final Consumer<MouseButtonEvent> onClick;
	private final Integer styleColor;
	private String narration;

	KubeUIRichText(int x, int y, int width, int height, Component text, Font font, Consumer<MouseButtonEvent> onClick, Integer styleColor) {
		super(x, y, width, height, text);
		this.font = font;
		this.onClick = onClick;
		this.styleColor = styleColor;
		this.active = onClick != null;
	}

	static int wrappedHeight(Font font, Component text, int width) {
		return Math.max(font.lineHeight, font.split(text, Math.max(1, width)).size() * font.lineHeight);
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	public void onClick(double mouseX, double mouseY, int button) {
		onClick(new MouseButtonEvent(mouseX, mouseY, button), doubleClickTracker.registerClick(mouseX, mouseY));
	}

	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (onClick != null) {
			onClick.accept(event);
		}
	}

	@Override
	protected void renderWidget(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		extractWidgetRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (active && isHovered()) {
			graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x30FFFFFF);
		}
		int color = styleColor != null ? styleColor : KubeUITheme.textColor();
		KubeUIFontScale.draw(graphics, getX(), getY(), () ->
			graphics.textWithWordWrap(font, getMessage(), getX(), getY(), getWidth(), color));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, narration != null ? Component.literal(narration) : getMessage());
	}
}
