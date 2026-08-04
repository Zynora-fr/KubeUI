package dev.kubeui.gui;

/// Called by `.rangeSlider(...)` whenever either handle moves - a plain `BiConsumer` only has
/// room for one value besides the screen, and a range needs two.
@FunctionalInterface
public interface KubeUIRangeChangeListener {
	void onChange(KubeUIContext screen, double low, double high);
}
