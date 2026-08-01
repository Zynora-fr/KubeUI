package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/// Small square handle in a scrollable panel's bottom-right corner - dragging it vertically
/// resizes the panel (see `.resizable(...)`).
class KubeUIResizeHandle extends AbstractWidget {
	private final DoubleConsumer onDragY;

	KubeUIResizeHandle(int x, int y, int size, DoubleConsumer onDragY) {
		super(x, y, size, size, Component.literal("Resize"));
		this.onDragY = onDragY;
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		onDragY.accept(dy);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + getWidth();
		int y1 = y0 + getHeight();

		graphics.fill(x0, y0, x1, y1, isHovered() ? 0xFFAAAAAA : 0xFF666666);
		graphics.fill(x0, y0, x1, y0 + 1, 0xFF1E1E1E);
		graphics.fill(x0, y0, x0 + 1, y1, 0xFF1E1E1E);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
	}
}
