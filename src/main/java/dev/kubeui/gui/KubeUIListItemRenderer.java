package dev.kubeui.gui;

/// Called once per item by `KubeUIScreenBuilder#list`, to build that item's row.
@FunctionalInterface
public interface KubeUIListItemRenderer {
	void render(KubeUIScreenBuilder row, Object item, int index);
}
