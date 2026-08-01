package dev.kubeui.gui;

/// Implemented by KubeUI's own custom widgets (divider, spacer, progress bar, image, item icon,
/// color swatch) to support `.narration(text)`, which overrides their otherwise-empty/generic
/// default accessibility narration. Vanilla widgets (button, checkbox, text field, ...) already
/// narrate their own label/value and aren't affected by `.narration(text)`.
interface KubeUINarratable {
	void setCustomNarration(String text);
}
