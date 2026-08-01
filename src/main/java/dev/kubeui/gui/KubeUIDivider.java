package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/// A plain horizontal separator line.
class KubeUIDivider extends AbstractWidget implements KubeUINarratable {
	private String narration;

	KubeUIDivider(int x, int y, int width, int height) {
		super(x, y, width, height, Component.empty());
		this.active = false;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int midY = getY() + getHeight() / 2;
		graphics.fill(getX(), midY, getX() + getWidth(), midY + 1, 0x55FFFFFF);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		if (narration != null) {
			output.add(NarratedElementType.TITLE, Component.literal(narration));
		}
	}
}
