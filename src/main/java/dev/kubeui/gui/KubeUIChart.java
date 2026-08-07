package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.List;

/// A simple bar or line chart (`.chart(id, kind, values)` / `.chart(id, kind, values, labels)`),
/// for showing script-side numbers (progress over time, server economy stats, ...) without
/// exporting them anywhere else. `kind` is `"bar"` or `"line"` - anything else falls back to bars.
/// `GuiGraphicsExtractor` only exposes axis-aligned `fill()`, no line-drawing primitive, so `"line"`
/// is drawn as a real (not faked/stepped) diagonal by interpolating a y position per screen column
/// between each pair of points and filling a thin rect at each one.
class KubeUIChart extends AbstractWidget implements KubeUINarratable {
	private static final int POINT_SIZE = 3;

	private final String kind;
	private final List<Double> values;
	private final List<String> labels;
	private final Font font;
	private final Integer styleColor;
	private final Integer styleAccent;
	private String narration;

	KubeUIChart(int x, int y, int width, int height, String kind, List<Double> values, List<String> labels, Font font, Integer styleColor, Integer styleAccent) {
		super(x, y, width, height, Component.literal("Chart"));
		this.kind = kind;
		this.values = values;
		this.labels = labels;
		this.font = font;
		this.styleColor = styleColor;
		this.styleAccent = styleAccent;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (values.isEmpty()) {
			return;
		}

		boolean hasLabels = labels != null && !labels.isEmpty();
		int labelHeight = hasLabels ? font.lineHeight + 2 : 0;
		int chartHeight = Math.max(1, getHeight() - labelHeight);
		int chartBottom = getY() + chartHeight;

		double max = 0;
		for (double v : values) {
			max = Math.max(max, v);
		}
		max = Math.max(max, 1e-6);

		int accent = styleAccent != null ? styleAccent : KubeUITheme.accentColor();
		int text = styleColor != null ? styleColor : KubeUITheme.textColor();

		if ("line".equals(kind)) {
			renderLine(graphics, max, chartBottom, chartHeight, accent, text);
		} else {
			renderBars(graphics, max, chartBottom, chartHeight, accent);
		}

		if (hasLabels) {
			int slotWidth = getWidth() / values.size();
			for (int i = 0; i < labels.size() && i < values.size(); i++) {
				int slotX = getX() + i * slotWidth;
				int lx = slotX + slotWidth / 2;
				int ly = chartBottom + 1;
				String label = labels.get(i);
				KubeUIFontScale.draw(graphics, lx, ly, () -> graphics.centeredText(font, label, lx, ly, text));
			}
		}
	}

	private void renderBars(GuiGraphicsExtractor graphics, double max, int chartBottom, int chartHeight, int accent) {
		int slotWidth = getWidth() / values.size();
		for (int i = 0; i < values.size(); i++) {
			int barHeight = (int) Math.round(values.get(i) / max * chartHeight);
			int barX = getX() + i * slotWidth;
			graphics.fill(barX + 1, chartBottom - barHeight, barX + slotWidth - 1, chartBottom, accent);
		}
	}

	private void renderLine(GuiGraphicsExtractor graphics, double max, int chartBottom, int chartHeight, int accent, int text) {
		int[] xs = new int[values.size()];
		int[] ys = new int[values.size()];

		for (int i = 0; i < values.size(); i++) {
			xs[i] = values.size() == 1 ? getX() + getWidth() / 2 : getX() + i * (getWidth() - 1) / (values.size() - 1);
			ys[i] = chartBottom - (int) Math.round(values.get(i) / max * chartHeight);
		}

		for (int i = 0; i < xs.length - 1; i++) {
			int x1 = xs[i];
			int x2 = xs[i + 1];
			int y1 = ys[i];
			int y2 = ys[i + 1];
			int steps = Math.max(1, Math.abs(x2 - x1));

			for (int s = 0; s <= steps; s++) {
				double t = (double) s / steps;
				int px = x1 + (int) Math.round((x2 - x1) * t);
				int py = y1 + (int) Math.round((y2 - y1) * t);
				graphics.fill(px, py - 1, px + 1, py + 2, accent);
			}
		}

		for (int i = 0; i < xs.length; i++) {
			graphics.fill(xs[i] - POINT_SIZE / 2, ys[i] - POINT_SIZE / 2, xs[i] + POINT_SIZE / 2 + 1, ys[i] + POINT_SIZE / 2 + 1, text);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : kind + " chart, " + values.size() + " values"));
	}
}
