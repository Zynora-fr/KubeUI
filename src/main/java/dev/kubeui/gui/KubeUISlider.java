package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/// A slider remapping the internal [0, 1] position to a [min, max] value range.
/// Displays whole numbers when both bounds are integral, one decimal place otherwise.
class KubeUISlider extends AbstractSliderButton {
	private final double min;
	private final double max;
	private final boolean wholeNumbers;
	private final KubeUIContext context;
	private final BiConsumer<KubeUIContext, Double> onChange;

	KubeUISlider(int x, int y, int width, int height, double min, double max, double initial, KubeUIContext context, BiConsumer<KubeUIContext, Double> onChange) {
		super(x, y, width, height, Component.empty(), toNormalized(min, max, initial));
		this.min = min;
		this.max = max;
		this.wholeNumbers = min == Math.floor(min) && max == Math.floor(max);
		this.context = context;
		this.onChange = onChange;
		updateMessage();
	}

	private static double toNormalized(double min, double max, double actual) {
		return max > min ? Math.min(1.0, Math.max(0.0, (actual - min) / (max - min))) : 0.0;
	}

	double currentValue() {
		return min + value * (max - min);
	}

	void setCurrentValue(double newValue) {
		setValue(toNormalized(min, max, newValue));
	}

	@Override
	protected void updateMessage() {
		double v = currentValue();
		setMessage(Component.literal(wholeNumbers ? String.valueOf(Math.round(v)) : String.format("%.1f", v)));
	}

	@Override
	protected void applyValue() {
		if (onChange != null) {
			onChange.accept(context, currentValue());
		}
	}
}
