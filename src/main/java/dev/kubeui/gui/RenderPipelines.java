package dev.kubeui.gui;

/// 1.21.1 has no pipeline concept for 2D GUI blits - this placeholder exists only so call sites
/// written against 26.1.2's `blit(RenderPipelines.GUI_TEXTURED, ...)` convention compile
/// unchanged; [GuiGraphicsExtractor]'s `blit(...)` overloads accept and ignore it.
final class RenderPipelines {
	static final Object GUI_TEXTURED = new Object();

	private RenderPipelines() {
	}
}
