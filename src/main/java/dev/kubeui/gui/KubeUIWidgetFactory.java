package dev.kubeui.gui;

import net.minecraft.client.gui.layouts.LayoutElement;

/// Implemented by third-party **Java** mods (not KubeJS scripts) to add new widget types to
/// KubeUI. Register with [KubeUIWidgets#register], then scripts can use it via
/// `KubeUI.builder(...).custom("yourType", ...args)`.
@FunctionalInterface
public interface KubeUIWidgetFactory {
	LayoutElement create(KubeUIWidgetFactoryContext context);
}
