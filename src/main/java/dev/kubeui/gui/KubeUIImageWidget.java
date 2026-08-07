package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/// Renders a whole custom texture, scaled to fill the widget's bounds.
class KubeUIImageWidget extends AbstractWidget implements KubeUINarratable {
	private final ResourceLocation texture;
	private String narration;

	KubeUIImageWidget(int x, int y, int width, int height, ResourceLocation texture) {
		super(x, y, width, height, Component.empty());
		this.active = false;
		this.texture = texture;
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
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, getX(), getY(), 0, 0, getWidth(), getHeight(), getWidth(), getHeight());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		if (narration != null) {
			output.add(NarratedElementType.TITLE, Component.literal(narration));
		}
	}
}
