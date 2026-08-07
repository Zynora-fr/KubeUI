package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/// A single track with two independently draggable handles
/// (`.rangeSlider(id, min, max, initialLow, initialHigh, onChange)`), for picking an interval
/// instead of one value. Not `AbstractSliderButton`-based like [KubeUISlider] - that class's whole
/// model assumes exactly one draggable position, so this implements its own hit-testing/dragging
/// directly on top of `AbstractWidget`.
class KubeUIRangeSlider extends AbstractWidget implements KubeUINarratable {
	private static final int HANDLE_WIDTH = 4;
	private static final int TRACK_HEIGHT = 2;

	private final double min;
	private final double max;
	private final Font font;
	private final KubeUIContext context;
	private final KubeUIRangeChangeListener onChange;
	private double low;
	private double high;
	private boolean draggingHigh;
	private String narration;
	private final Integer styleColor;

	KubeUIRangeSlider(int x, int y, int width, int height, double min, double max, double initialLow, double initialHigh, Font font, KubeUIContext context, KubeUIRangeChangeListener onChange, Integer styleColor) {
		super(x, y, width, height, Component.literal("Range"));
		this.min = min;
		this.max = max;
		this.low = clamp(Math.min(initialLow, initialHigh));
		this.high = clamp(Math.max(initialLow, initialHigh));
		this.font = font;
		this.context = context;
		this.onChange = onChange;
		this.styleColor = styleColor;
	}

	double low() {
		return low;
	}

	double high() {
		return high;
	}

	void setRange(double newLow, double newHigh) {
		this.low = clamp(Math.min(newLow, newHigh));
		this.high = clamp(Math.max(newLow, newHigh));
	}

	private double clamp(double v) {
		return Math.max(min, Math.min(max, v));
	}

	private double xToValue(double mouseX) {
		double t = getWidth() <= 0 ? 0 : (mouseX - getX()) / (double) getWidth();
		return clamp(min + Math.max(0, Math.min(1, t)) * (max - min));
	}

	private int valueToX(double v) {
		double t = max > min ? (v - min) / (max - min) : 0;
		return getX() + (int) Math.round(t * getWidth());
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		int lowX = valueToX(low);
		int highX = valueToX(high);
		draggingHigh = Math.abs(event.x() - highX) <= Math.abs(event.x() - lowX);
		updateFromMouse(event.x());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		updateFromMouse(event.x());
	}

	private void updateFromMouse(double mouseX) {
		double v = xToValue(mouseX);
		if (draggingHigh) {
			high = Math.max(v, low);
		} else {
			low = Math.min(v, high);
		}
		if (onChange != null) {
			onChange.onChange(context, low, high);
		}
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	/// Keyboard equivalent of dragging: Up/Down switches which of the two handles Left/Right
	/// adjusts (mirrors `draggingHigh`, the same field mouse-dragging already uses) - needed
	/// because, unlike `.slider()`/`.steppedSlider()` (`AbstractSliderButton`-based, which already
	/// get arrow-key support for free from vanilla), this widget implements its own hit-testing
	/// directly on `AbstractWidget`, which doesn't provide any keyboard handling on its own.
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isUp() || event.isDown()) {
			draggingHigh = !draggingHigh;
			return true;
		}

		if (event.isLeft() || event.isRight()) {
			double step = Math.max((max - min) / 100.0, 1e-6);
			double delta = (event.isRight() ? 1 : -1) * step;
			if (draggingHigh) {
				high = clamp(Math.max(low, high + delta));
			} else {
				low = Math.min(high, clamp(low + delta));
			}
			if (onChange != null) {
				onChange.onChange(context, low, high);
			}
			return true;
		}

		return false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		String label = formatValue(low) + " - " + formatValue(high);
		int color = styleColor != null ? styleColor : KubeUITheme.textColor();
		int lx = getX() + getWidth() / 2;
		int ly = getY();
		KubeUIFontScale.draw(graphics, lx, ly, () -> graphics.centeredText(font, label, lx, ly, color));

		int trackTop = getY() + font.lineHeight + 2;
		int trackBottom = getY() + getHeight();
		int trackY = trackTop + (trackBottom - trackTop) / 2 - TRACK_HEIGHT / 2;
		graphics.fill(getX(), trackY, getX() + getWidth(), trackY + TRACK_HEIGHT, 0xFF6B7679);

		int lowX = valueToX(low);
		int highX = valueToX(high);
		graphics.fill(lowX, trackY, highX, trackY + TRACK_HEIGHT, 0xFF45D6C9);

		graphics.fill(lowX - HANDLE_WIDTH / 2, trackTop, lowX + HANDLE_WIDTH / 2, trackBottom, 0xFFEAF3F3);
		graphics.fill(highX - HANDLE_WIDTH / 2, trackTop, highX + HANDLE_WIDTH / 2, trackBottom, 0xFFEAF3F3);

		KubeUIFocusOutline.draw(graphics, this);
	}

	private String formatValue(double v) {
		return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		String text = narration != null ? narration : String.format("%.1f - %.1f", low, high);
		output.add(NarratedElementType.TITLE, Component.literal(text));
	}
}
