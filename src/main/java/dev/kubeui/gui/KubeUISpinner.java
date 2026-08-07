package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/// An indeterminate loading spinner (`.spinner(id)`), for "waiting on the server"
/// moments (e.g. right after `screen.runServerAction(...)`, before the response arrives). Eight
/// dots around a ring, brightness cycling with wall-clock time - no pose-stack rotation needed,
/// just `sin`/`cos` for dot positions and a phase offset per dot for the "chasing" look.
class KubeUISpinner extends AbstractWidget implements KubeUINarratable {
	private static final int DOTS = 8;
	private static final int PERIOD_MS = 800;

	private String narration;

	KubeUISpinner(int x, int y, int size) {
		super(x, y, size, size, Component.literal("Loading"));
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
		int cx = getX() + getWidth() / 2;
		int cy = getY() + getHeight() / 2;
		double radius = Math.min(getWidth(), getHeight()) / 2.0 - 2;
		double phase = (System.currentTimeMillis() % PERIOD_MS) / (double) PERIOD_MS;

		for (int i = 0; i < DOTS; i++) {
			double angle = 2 * Math.PI * i / DOTS;
			int dx = cx + (int) Math.round(Math.cos(angle) * radius);
			int dy = cy + (int) Math.round(Math.sin(angle) * radius);

			double brightness = 0.25 + 0.75 * (1 - ((i / (double) DOTS - phase + 1) % 1));
			int alpha = KubeUILayoutMath.clampInt((int) (brightness * 255), 40, 255);
			int color = (alpha << 24) | 0xFFFFFF;

			graphics.fill(dx - 1, dy - 1, dx + 1, dy + 1, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : "Loading"));
	}
}
