package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/// A clickable flat color square, used by the color picker's preset palette.
class KubeUIColorSwatch extends AbstractWidget {
	private final DoubleClickTracker doubleClickTracker = new DoubleClickTracker();
	private final int color;
	private final IntConsumer onClick;

	KubeUIColorSwatch(int x, int y, int size, int color, IntConsumer onClick) {
		super(x, y, size, size, Component.empty());
		this.color = color;
		this.onClick = onClick;
	}

	@Override
	public void onClick(double mouseX, double mouseY, int button) {
		onClick(new MouseButtonEvent(mouseX, mouseY, button), doubleClickTracker.registerClick(mouseX, mouseY));
	}

	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		onClick.accept(color);
	}

	@Override
	protected void renderWidget(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		extractWidgetRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF000000 | color);

		int border = isHovered() ? 0xFFFFFFFF : 0xFF1E1E1E;
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, border);
		graphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(), border);
		graphics.fill(getX(), getY(), getX() + 1, getY() + getHeight(), border);
		graphics.fill(getX() + getWidth() - 1, getY(), getX() + getWidth(), getY() + getHeight(), border);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(String.format("Color #%06X", color & 0xFFFFFF)));
	}
}
