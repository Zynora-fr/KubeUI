package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;

/// The draggable bar between the two panes of a `.splitPane(...)` - same drag-callback shape as
/// [KubeUIResizeHandle], just horizontal instead of vertical.
class KubeUISplitPaneDivider extends AbstractWidget {
	private final DoubleConsumer onDragX;

	KubeUISplitPaneDivider(int x, int y, int width, int height, DoubleConsumer onDragX) {
		super(x, y, width, height, Component.literal("Resize panes"));
		this.onDragX = onDragX;
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		onDragX.accept(dx);
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), isHovered() ? 0xFFEAF3F3 : 0xFF6B7679);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, getMessage());
	}
}
