package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/// Read-only progress bar. Value/max are updated externally via [KubeUIContext#setProgress].
class KubeUIProgressBar extends AbstractWidget implements KubeUINarratable {
	private final Font font;
	private double value;
	private double max;
	private String narration;
	private final Integer styleAccent;

	KubeUIProgressBar(int x, int y, int width, int height, double value, double max, Font font, Integer styleAccent) {
		super(x, y, width, height, Component.empty());
		this.active = false;
		this.value = value;
		this.max = max;
		this.font = font;
		this.styleAccent = styleAccent;
	}

	double value() {
		return value;
	}

	double maxValue() {
		return max;
	}

	void set(double value, double max) {
		this.value = value;
		this.max = max;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int x0 = getX();
		int y0 = getY();
		int x1 = x0 + getWidth();
		int y1 = y0 + getHeight();

		graphics.fill(x0, y0, x1, y1, 0xFF373737);

		double ratio = max > 0 ? Math.min(1.0, Math.max(0.0, value / max)) : 0.0;
		int filledX = x0 + (int) ((x1 - x0) * ratio);
		if (filledX > x0) {
			graphics.fill(x0, y0, filledX, y1, styleAccent != null ? styleAccent : KubeUITheme.accentColor());
		}

		graphics.fill(x0, y0, x1, y0 + 1, 0xFF1E1E1E);
		graphics.fill(x0, y1 - 1, x1, y1, 0xFF1E1E1E);
		graphics.fill(x0, y0, x0 + 1, y1, 0xFF1E1E1E);
		graphics.fill(x1 - 1, y0, x1, y1, 0xFF1E1E1E);

		String pct = Math.round(ratio * 100) + "%";
		int lx = x0 + getWidth() / 2;
		int ly = y0 + (getHeight() - 8) / 2;
		KubeUIFontScale.draw(graphics, lx, ly, () -> graphics.centeredText(font, pct, lx, ly, 0xFFFFFFFF));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		double ratio = max > 0 ? Math.min(1.0, Math.max(0.0, value / max)) : 0.0;
		String text = narration != null ? narration : "Progress: " + Math.round(ratio * 100) + "%";
		output.add(NarratedElementType.TITLE, Component.literal(text));
	}
}
