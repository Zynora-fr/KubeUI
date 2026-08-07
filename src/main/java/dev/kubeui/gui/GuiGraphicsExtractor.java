package dev.kubeui.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/// Compatibility shim reproducing the 26.1.2 `GuiGraphicsExtractor` API surface (the subset
/// KubeUI actually uses) on top of 1.21.1's real, immediate-mode `GuiGraphics` - so every
/// widget's `extractWidgetRenderState`/`extractBackground`/`extractRenderState` body can stay
/// unchanged, only the bridge point where the real vanilla lifecycle hands off to KubeUI needs
/// to wrap a real `GuiGraphics` into one of these first.
final class GuiGraphicsExtractor {
	private final GuiGraphics delegate;
	private final PoseHandle poseHandle;

	GuiGraphicsExtractor(GuiGraphics delegate) {
		this.delegate = delegate;
		this.poseHandle = new PoseHandle(delegate.pose());
	}

	GuiGraphics real() {
		return delegate;
	}

	void fill(int x0, int y0, int x1, int y1, int color) {
		delegate.fill(x0, y0, x1, y1, color);
	}

	void outline(int x, int y, int width, int height, int color) {
		delegate.renderOutline(x, y, width, height, color);
	}

	int guiWidth() {
		return delegate.guiWidth();
	}

	PoseHandle pose() {
		return poseHandle;
	}

	void text(Font font, String str, int x, int y, int color, boolean dropShadow) {
		delegate.drawString(font, str, x, y, color, dropShadow);
	}

	void text(Font font, Component text, int x, int y, int color, boolean dropShadow) {
		delegate.drawString(font, text, x, y, color, dropShadow);
	}

	void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean dropShadow) {
		delegate.drawString(font, text, x, y, color, dropShadow);
	}

	void centeredText(Font font, String str, int x, int y, int color) {
		delegate.drawCenteredString(font, str, x, y, color);
	}

	void centeredText(Font font, Component text, int x, int y, int color) {
		delegate.drawCenteredString(font, text, x, y, color);
	}

	void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
		delegate.drawCenteredString(font, text, x, y, color);
	}

	void textWithWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
		delegate.drawWordWrap(font, text, x, y, width, color);
	}

	void item(ItemStack itemStack, int x, int y) {
		delegate.renderItem(itemStack, x, y);
	}

	void item(ItemStack itemStack, int x, int y, int seed) {
		delegate.renderItem(itemStack, x, y, seed);
	}

	void item(LivingEntity owner, ItemStack itemStack, int x, int y, int seed) {
		delegate.renderItem(owner, itemStack, x, y, seed);
	}

	void itemDecorations(Font font, ItemStack itemStack, int x, int y) {
		delegate.renderItemDecorations(font, itemStack, x, y);
	}

	void itemDecorations(Font font, ItemStack itemStack, int x, int y, String countText) {
		delegate.renderItemDecorations(font, itemStack, x, y, countText);
	}

	void fakeItem(ItemStack itemStack, int x, int y) {
		delegate.renderFakeItem(itemStack, x, y);
	}

	void fakeItem(ItemStack itemStack, int x, int y, int seed) {
		delegate.renderFakeItem(itemStack, x, y, seed);
	}

	/// `.background("blur")` forces a real GPU blur behind the screen in 26.1.2 via this call.
	/// 1.21.1 has no equivalent "blur this render stratum" primitive - the vanilla blur is a
	/// menu-background-only post-process the game itself decides to run, not something a screen
	/// can request on demand. No-op here; `.background("blur")` degrades to the plain dark-tint
	/// fallback on this version instead of a real blur.
	void blurBeforeThisStratum() {
	}

	/// Simple 1:1 blit (destination size == source crop size in pixels) - the common case for a
	/// non-atlas custom texture sized to exactly fill its destination. `pipeline` is accepted and
	/// ignored (1.21.1 has no `RenderPipelines`/pipeline concept for 2D blits at all); kept as a
	/// parameter purely so call sites written against 26.1.2's `blit(RenderPipelines.GUI_TEXTURED,
	/// ...)` convention don't need touching.
	void blit(Object pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
		delegate.blit(texture, x, y, u, v, width, height, textureWidth, textureHeight);
	}

	/// Independently-scaled textured-quad blit: `srcWidth`/`srcHeight` (pixels within
	/// `sheetWidth`/`sheetHeight`) is the source crop, `width`/`height` is the on-screen
	/// destination size - the two are allowed to differ (the whole point for nine-slice corners/
	/// edges). 1.21.1's public `GuiGraphics.blit(...)` overloads all tie destination size to
	/// source crop size 1:1; the one real overload that does NOT (`innerBlit`) is package-private
	/// to `net.minecraft.client.gui`, so this reproduces it directly - same shader, same vertex
	/// format, same call sequence as vanilla's own `innerBlit(ResourceLocation, x1, x2, y1, y2,
	/// blitOffset, u0, u1, v0, v1)`.
	void blit(Object pipeline, ResourceLocation texture, int x, int y, int u, int v, int width, int height, int srcWidth, int srcHeight, int sheetWidth, int sheetHeight) {
		if (width <= 0 || height <= 0) {
			return;
		}
		float u0 = u / (float) sheetWidth;
		float u1 = (u + srcWidth) / (float) sheetWidth;
		float v0 = v / (float) sheetHeight;
		float v1 = (v + srcHeight) / (float) sheetHeight;

		RenderSystem.setShaderTexture(0, texture);
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		Matrix4f matrix = delegate.pose().last().pose();
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		buffer.addVertex(matrix, x, y, 0).setUv(u0, v0);
		buffer.addVertex(matrix, x, y + height, 0).setUv(u0, v1);
		buffer.addVertex(matrix, x + width, y + height, 0).setUv(u1, v1);
		buffer.addVertex(matrix, x + width, y, 0).setUv(u1, v0);
		BufferUploader.drawWithShader(buffer.buildOrThrow());
	}

	/// Mirrors the small 2D-convenience subset of 26.1.2's `Matrix3x2fStack` (`pushMatrix`/
	/// `popMatrix`/`scaleAround`/`translate`) that KubeUI's own render code uses, backed by
	/// 1.21.1's real `PoseStack` (a 3D matrix stack - every 2D op here is a Z=0 special case).
	static final class PoseHandle {
		private final PoseStack delegate;

		private PoseHandle(PoseStack delegate) {
			this.delegate = delegate;
		}

		void pushMatrix() {
			delegate.pushPose();
		}

		void popMatrix() {
			delegate.popPose();
		}

		void translate(float dx, float dy) {
			delegate.translate(dx, dy, 0);
		}

		void scaleAround(float scale, float pivotX, float pivotY) {
			delegate.translate(pivotX, pivotY, 0);
			delegate.scale(scale, scale, 1);
			delegate.translate(-pivotX, -pivotY, 0);
		}
	}
}
