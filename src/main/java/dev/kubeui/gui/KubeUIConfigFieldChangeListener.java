package dev.kubeui.gui;

/// Called by `.configScreen(...)` whenever any one field changes - a single listener for the whole
/// screen (rather than one per field) so a script can react generically (e.g. re-save all of them
/// at once) without wiring a separate callback per field it declares in the schema.
@FunctionalInterface
public interface KubeUIConfigFieldChangeListener {
	void onChange(KubeUIContext screen, String fieldId, Object newValue);
}
