package dev.kubeui.gui;

/// Called once per step by `KubeUIScreenBuilder#wizard`, whenever that step becomes the active
/// one, to build its content. Same pattern as `KubeUIListItemRenderer` for `.list(...)`.
@FunctionalInterface
public interface KubeUIWizardStepRenderer {
	void render(KubeUIScreenBuilder step, int index);
}
