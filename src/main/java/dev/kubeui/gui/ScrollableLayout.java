package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/// Compatibility shim mirroring 26.1.2's real `net.minecraft.client.gui.components
/// .ScrollableLayout` (wraps an arbitrary [Layout] in a scrollable area, sized up to a max
/// height) - 1.21.1 has no equivalent at all (this whole concept - a generic "make any layout
/// scrollable" wrapper - postdates it). Same public surface as the original
/// (`setMinWidth`/`setMinHeight`/`setMaxHeight`/the real `Layout` interface), same internal shape
/// (a private [AbstractScrollArea] subclass hosting `content`'s widgets), so every call site
/// written against the original needs no changes beyond the import already resolving here via
/// same-package lookup.
final class ScrollableLayout implements Layout {
	private final Layout content;
	private final Container container;
	private int minWidth;
	private int minHeight;
	private int maxHeight;

	ScrollableLayout(Minecraft minecraft, Layout content, int maxHeight) {
		this.content = content;
		this.maxHeight = maxHeight;
		this.container = new Container(0, maxHeight);
	}

	void setMinWidth(int minWidth) {
		this.minWidth = minWidth;
		container.setWidth(Math.max(content.getWidth(), minWidth));
	}

	void setMinHeight(int minHeight) {
		this.minHeight = minHeight;
		container.setHeight(Math.max(content.getHeight(), minHeight));
	}

	void setMaxHeight(int maxHeight) {
		this.maxHeight = maxHeight;
		container.setHeight(Math.min(content.getHeight(), maxHeight));
		container.resyncScroll();
	}

	@Override
	public void arrangeElements() {
		content.arrangeElements();
		int contentWidth = content.getWidth();
		int scrollbarReserve = container.scrollbarWidth() + 4;
		container.setWidth(Math.max(contentWidth, minWidth) + scrollbarReserve);
		container.setHeight(Math.max(minHeight, Math.min(content.getHeight(), maxHeight)));
		container.resyncScroll();
	}

	@Override
	public void visitChildren(Consumer<LayoutElement> visitor) {
		visitor.accept(container);
	}

	@Override
	public void setX(int x) {
		container.setX(x);
	}

	@Override
	public void setY(int y) {
		container.setY(y);
	}

	@Override
	public int getX() {
		return container.getX();
	}

	@Override
	public int getY() {
		return container.getY();
	}

	@Override
	public int getWidth() {
		return container.getWidth();
	}

	@Override
	public int getHeight() {
		return container.getHeight();
	}

	private final class Container extends AbstractScrollArea {
		private final List<AbstractWidget> children = new ArrayList<>();

		Container(int width, int height) {
			super(0, 0, width, height, Component.empty());
			content.visitWidgets(children::add);
		}

		@Override
		int contentHeight() {
			return content.getHeight();
		}

		/// Keeps `content`'s actual position - and every child widget's real, independently
		/// hit-tested `getX()`/`getY()` along with it - in sync with the current scroll amount,
		/// after anything that could have changed either (a resize, a manual `setScrollAmount`).
		void resyncScroll() {
			setScrollAmount(scrollAmount());
			content.setY(getY() - (int) scrollAmount());
		}

		@Override
		public void setX(int x) {
			super.setX(x);
			content.setX(x);
		}

		@Override
		public void setY(int y) {
			super.setY(y);
			content.setY(y - (int) scrollAmount());
		}

		@Override
		protected void setScrollAmount(double amount) {
			super.setScrollAmount(amount);
			content.setY(getY() - (int) scrollAmount());
		}

		@Override
		public List<? extends GuiEventListener> children() {
			return children;
		}

		public Collection<? extends NarratableEntry> getNarratables() {
			return List.of();
		}

		@Override
		protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		}
	}
}
