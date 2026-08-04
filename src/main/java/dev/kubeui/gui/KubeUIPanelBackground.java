package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/// A nine-patch/nine-slice background (`.panelBackground(texture, width, height)`):
/// corners drawn at fixed size, edges stretched along one axis, the center stretched both ways -
/// so a panel can grow to any size without visibly stretching its border art, unlike a plain
/// `.image(...)` (which stretches the *whole* texture, border included).
///
/// There's no data-driven sprite-scaling metadata plumbed in here (the real vanilla nine-slice
/// system - `GuiSpriteScaling.NineSlice` - is resource-pack-JSON-driven and its blit method is
/// private); this draws the 9 regions itself via plain `blit(...)` calls instead. Fixed
/// convention: the source texture is assumed 32x32 with an 8px border on every side - not
/// currently configurable, but real, not stretched art passed off as a nine-slice.
class KubeUIPanelBackground extends AbstractWidget implements KubeUINarratable {
	private static final int TEXTURE_SIZE = 32;
	private static final int BORDER = 8;

	private final Identifier texture;
	private String narration;

	KubeUIPanelBackground(int x, int y, int width, int height, Identifier texture) {
		super(x, y, width, height, Component.empty());
		this.texture = texture;
		this.active = false;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x = getX();
		int y = getY();
		int w = getWidth();
		int h = getHeight();
		int b = Math.min(BORDER, Math.min(w, h) / 2);
		int innerW = Math.max(0, w - b * 2);
		int innerH = Math.max(0, h - b * 2);
		int srcInner = TEXTURE_SIZE - BORDER * 2;

		// corners (fixed size, no stretch)
		blitRegion(graphics, x, y, b, b, 0, 0, BORDER, BORDER);
		blitRegion(graphics, x + b + innerW, y, b, b, TEXTURE_SIZE - BORDER, 0, BORDER, BORDER);
		blitRegion(graphics, x, y + b + innerH, b, b, 0, TEXTURE_SIZE - BORDER, BORDER, BORDER);
		blitRegion(graphics, x + b + innerW, y + b + innerH, b, b, TEXTURE_SIZE - BORDER, TEXTURE_SIZE - BORDER, BORDER, BORDER);

		// edges (stretched along one axis)
		if (innerW > 0) {
			blitRegion(graphics, x + b, y, innerW, b, BORDER, 0, srcInner, BORDER);
			blitRegion(graphics, x + b, y + b + innerH, innerW, b, BORDER, TEXTURE_SIZE - BORDER, srcInner, BORDER);
		}
		if (innerH > 0) {
			blitRegion(graphics, x, y + b, b, innerH, 0, BORDER, BORDER, srcInner);
			blitRegion(graphics, x + b + innerW, y + b, b, innerH, TEXTURE_SIZE - BORDER, BORDER, BORDER, srcInner);
		}

		// center (stretched both axes)
		if (innerW > 0 && innerH > 0) {
			blitRegion(graphics, x + b, y + b, innerW, innerH, BORDER, BORDER, srcInner, srcInner);
		}
	}

	private void blitRegion(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int u, int v, int srcWidth, int srcHeight) {
		if (width <= 0 || height <= 0) {
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, srcWidth, srcHeight, TEXTURE_SIZE, TEXTURE_SIZE);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		if (narration != null) {
			output.add(NarratedElementType.TITLE, Component.literal(narration));
		}
	}
}
