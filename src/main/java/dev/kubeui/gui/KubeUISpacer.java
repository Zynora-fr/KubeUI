package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/// Invisible - reserves layout space and nothing else.
class KubeUISpacer extends AbstractWidget implements KubeUINarratable {
	private String narration;

	KubeUISpacer(int width, int height) {
		super(0, 0, width, height, Component.empty());
		this.visible = false;
		this.active = false;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		if (narration != null) {
			output.add(NarratedElementType.TITLE, Component.literal(narration));
		}
	}
}
