package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/// A nine-patch/nine-slice background (`.panelBackground(texture, width, height)`):
/// corners drawn at fixed size, edges stretched along one axis, the center stretched both ways -
/// so a panel can grow to any size without visibly stretching its border art, unlike a plain
/// `.image(...)` (which stretches the *whole* texture, border included).
///
/// There's no data-driven sprite-scaling metadata plumbed in here (the real vanilla nine-slice
/// system - `GuiSpriteScaling.NineSlice` - is resource-pack-JSON-driven and its blit method is
/// private); this draws the 9 regions itself via plain `blit(...)` calls instead. Fixed
/// convention: the source texture is 64x64 with a 20px border band ([#BORDER_SRC]) - deliberately
/// bigger than the border ever actually draws on screen ([#BORDER_DST], 10px, separately clamped)
/// so a soft glow/rounded-corner border has real sub-pixel gradient data to downscale from instead
/// of a handful of flat-colored texels stretched thin. `blit(...)` scaling a source crop to a
/// differently-sized destination is a real, generic capability of the underlying textured-quad
/// blit (proven inside this very class already - the edge regions stretch their fixed-thickness
/// source *length* to an arbitrary destination length), so using it for the border *thickness*
/// too, rather than a straight 1:1 source-size bump, costs nothing extra and avoids the border
/// simply rendering twice as thick on screen.
class KubeUIPanelBackground extends AbstractWidget implements KubeUINarratable {
	private static final int TEXTURE_SIZE = 64;
	private static final int BORDER_SRC = 20;
	private static final int BORDER_DST = 10;

	private final ResourceLocation texture;
	private String narration;

	KubeUIPanelBackground(int x, int y, int width, int height, ResourceLocation texture) {
		super(x, y, width, height, Component.empty());
		this.texture = texture;
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
		draw(graphics, texture, getX(), getY(), getWidth(), getHeight());
	}

	/// The actual nine-slice draw, factored out so [KubeUIScreen]'s whole-window
	/// `.windowBackground(...)` can share it instead of duplicating the same 9-region math - same
	/// fixed 64x64-texture/20px-source-border/10px-screen-border convention either way. Corner and
	/// edge-thickness regions scale their `BORDER_SRC`-sized source crop down to the smaller `b`
	/// screen size, same as the edge regions already scale their source *length* up to an
	/// arbitrary screen length - one real blit call handles both directions of mismatch identically.
	static void draw(GuiGraphicsExtractor graphics, ResourceLocation texture, int x, int y, int w, int h) {
		int b = Math.min(BORDER_DST, Math.min(w, h) / 2);
		int innerW = Math.max(0, w - b * 2);
		int innerH = Math.max(0, h - b * 2);
		int srcInner = TEXTURE_SIZE - BORDER_SRC * 2;

		// corners (fixed size, no stretch)
		blitRegion(graphics, texture, x, y, b, b, 0, 0, BORDER_SRC, BORDER_SRC);
		blitRegion(graphics, texture, x + b + innerW, y, b, b, TEXTURE_SIZE - BORDER_SRC, 0, BORDER_SRC, BORDER_SRC);
		blitRegion(graphics, texture, x, y + b + innerH, b, b, 0, TEXTURE_SIZE - BORDER_SRC, BORDER_SRC, BORDER_SRC);
		blitRegion(graphics, texture, x + b + innerW, y + b + innerH, b, b, TEXTURE_SIZE - BORDER_SRC, TEXTURE_SIZE - BORDER_SRC, BORDER_SRC, BORDER_SRC);

		// edges (stretched along one axis)
		if (innerW > 0) {
			blitRegion(graphics, texture, x + b, y, innerW, b, BORDER_SRC, 0, srcInner, BORDER_SRC);
			blitRegion(graphics, texture, x + b, y + b + innerH, innerW, b, BORDER_SRC, TEXTURE_SIZE - BORDER_SRC, srcInner, BORDER_SRC);
		}
		if (innerH > 0) {
			blitRegion(graphics, texture, x, y + b, b, innerH, 0, BORDER_SRC, BORDER_SRC, srcInner);
			blitRegion(graphics, texture, x + b + innerW, y + b, b, innerH, TEXTURE_SIZE - BORDER_SRC, BORDER_SRC, BORDER_SRC, srcInner);
		}

		// center (stretched both axes)
		if (innerW > 0 && innerH > 0) {
			blitRegion(graphics, texture, x + b, y + b, innerW, innerH, BORDER_SRC, BORDER_SRC, srcInner, srcInner);
		}
	}

	private static void blitRegion(GuiGraphicsExtractor graphics, ResourceLocation texture, int x, int y, int width, int height, int u, int v, int srcWidth, int srcHeight) {
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
