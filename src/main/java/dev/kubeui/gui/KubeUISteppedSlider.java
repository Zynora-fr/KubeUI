package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

/// A slider over a fixed, ordered list of labeled steps rather than a numeric range
/// (`.steppedSlider(id, steps, initial, onChange)`) - the displayed text is the step's own label,
/// not a number. Reuses `AbstractSliderButton`'s continuous [0, 1] internal position (same base
/// class as [KubeUISlider]) and just rounds it to the nearest step index for display/callbacks.
class KubeUISteppedSlider extends AbstractSliderButton {
	private final List<String> steps;
	private final KubeUIContext context;
	private final BiConsumer<KubeUIContext, String> onChange;

	KubeUISteppedSlider(int x, int y, int width, int height, List<String> steps, String initial, KubeUIContext context, BiConsumer<KubeUIContext, String> onChange) {
		super(x, y, width, height, Component.empty(), normalizedFor(steps, initial));
		this.steps = steps;
		this.context = context;
		this.onChange = onChange;
		updateMessage();
	}

	private static double normalizedFor(List<String> steps, String initial) {
		int index = Math.max(0, steps.indexOf(initial));
		return steps.size() <= 1 ? 0.0 : index / (double) (steps.size() - 1);
	}

	private int currentIndex() {
		return steps.size() <= 1 ? 0 : (int) Math.round(value * (steps.size() - 1));
	}

	String currentStep() {
		return steps.get(currentIndex());
	}

	void setCurrentStep(String step) {
		int index = steps.indexOf(step);
		if (index >= 0) {
			// AbstractSliderButton#setValue is private in 1.21.1 - this replicates its real body
			// (clamp, then the same applyValue/updateMessage a real setValue call would trigger).
			this.value = net.minecraft.util.Mth.clamp(normalizedFor(steps, step), 0.0, 1.0);
			applyValue();
			updateMessage();
		}
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(currentStep()));
	}

	@Override
	protected void applyValue() {
		if (onChange != null) {
			onChange.accept(context, currentStep());
		}
	}
}
