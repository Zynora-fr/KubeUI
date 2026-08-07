package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/// Compatibility shim mirroring 26.1.2's real `net.minecraft.client.gui.components
/// .AbstractScrollArea` (a scrollable *container* of arbitrary child widgets) - 1.21.1 has no
/// direct equivalent, only `AbstractScrollWidget` (a scrollable *single-content* widget, e.g. a
/// text area, with no notion of children). This combines `AbstractScrollWidget`'s already-real,
/// already-correct scrollbar/wheel/keyboard-arrow scrolling with `ContainerEventHandler`'s
/// already-real child dispatch/focus/tab-navigation - `AbstractScrollWidget`'s own concrete
/// `mouseClicked`/`mouseDragged`/`keyPressed` would otherwise silently win over
/// `ContainerEventHandler`'s same-named defaults (class members always beat interface defaults),
/// so those three are overridden here to explicitly blend both: a scrollbar-region hit (or an
/// in-progress scrollbar drag) goes to the scroll logic, everything else falls through to child
/// dispatch.
abstract class AbstractScrollArea extends AbstractScrollWidget implements ContainerEventHandler {
	private boolean dragging;
	private GuiEventListener focused;

	AbstractScrollArea(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	// Re-declared here (same access level, just delegating straight to super) so this class's own
	// package (dev.kubeui.gui) - not AbstractScrollWidget's (net.minecraft.client.gui.components) -
	// governs same-package access: `protected` only reaches other dev.kubeui.gui classes
	// (KubeUIScreen, ScrollableLayout) through whichever class actually declares the member.
	@Override
	protected double scrollAmount() {
		return super.scrollAmount();
	}

	@Override
	protected void setScrollAmount(double amount) {
		super.setScrollAmount(amount);
	}

	private boolean overScrollbar(double mouseX, double mouseY) {
		return scrollbarVisible()
			&& mouseX >= getX() + width && mouseX <= getX() + width + scrollbarWidth()
			&& mouseY >= getY() && mouseY < getY() + height;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (visible && button == 0 && overScrollbar(mouseX, mouseY)) {
			return super.mouseClicked(mouseX, mouseY, button);
		}
		return ContainerEventHandler.super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (super.mouseDragged(mouseX, mouseY, button, dx, dy)) {
			return true;
		}
		return ContainerEventHandler.super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		boolean scrollHandled = super.mouseReleased(mouseX, mouseY, button);
		boolean childHandled = ContainerEventHandler.super.mouseReleased(mouseX, mouseY, button);
		return scrollHandled || childHandled;
	}

	@Override
	public boolean keyPressed(int keyCode, int scancode, int modifiers) {
		if (ContainerEventHandler.super.keyPressed(keyCode, scancode, modifiers)) {
			return true;
		}
		return super.keyPressed(keyCode, scancode, modifiers);
	}

	@Override
	protected int getInnerHeight() {
		return contentHeight();
	}

	/// Overridden to bypass `AbstractScrollWidget#getContentHeight()`'s built-in `+4` (a padding
	/// assumption specific to its own original single-text-field use case, real vanilla adds this
	/// so that a wrapped text field's content isn't flush against the border sprite - not
	/// applicable to an arbitrary widget container, and left unfixed made the deepest ~8px of
	/// content unreachable even at max scroll: `content.getHeight() - (height - 4)` overshoots
	/// vanilla's own `-4`, and the real, exact requirement is simply
	/// `contentHeight() - height` for content's bottom to land exactly on the container's bottom
	/// once fully scrolled.
	@Override
	protected int getMaxScrollAmount() {
		return Math.max(0, contentHeight() - height);
	}

	@Override
	protected double scrollRate() {
		return 10.0;
	}

	/// Overridden completely (not [#renderContents]) - unlike `AbstractScrollWidget`'s own
	/// single-content use case, child widgets here have real, independently hit-tested positions
	/// ([ScrollableLayout] keeps `content`'s actual x/y in sync with `scrollAmount()`), so the
	/// scroll offset must NOT also be applied as a render-time pose translate - that would shift
	/// what's drawn without shifting what's clickable, offsetting them from each other. Scissor +
	/// draw-at-real-position + the inherited scrollbar decoration is enough on its own.
	///
	/// Deliberately does *not* call `renderBackground(...)` - that draws a real vanilla text-field
	/// border sprite around the whole area, appropriate for `AbstractScrollWidget`'s own original
	/// single-text-field use case but not for an arbitrary KubeUI-themed widget container (26.1.2's
	/// real `ScrollableLayout.Container` never drew one either - scissor + children + scrollbar
	/// only). The scissor also isn't inset by 1px on each side anymore, since that inset existed
	/// only to keep content clear of that now-removed border.
	@Override
	public void renderWidget(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float a) {
		if (!visible) {
			return;
		}
		graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
		for (var child : children()) {
			if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
				widget.render(graphics, mouseX, mouseY, a);
			}
		}
		graphics.disableScissor();
		renderDecorations(graphics);
	}

	@Override
	protected void renderContents(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float a) {
		// Unused - renderWidget is overridden completely, see above.
	}

	abstract int contentHeight();

	@Nullable
	@Override
	public GuiEventListener getFocused() {
		return focused;
	}

	@Override
	public void setFocused(@Nullable GuiEventListener focused) {
		this.focused = focused;
	}

	@Override
	public boolean isDragging() {
		return dragging;
	}

	@Override
	public void setDragging(boolean dragging) {
		this.dragging = dragging;
	}
}
