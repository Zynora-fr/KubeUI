package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/// The floating menu `.contextMenu(items, onSelect)` opens on right-click, positioned
/// at the click location. Added directly via `Screen#addRenderableWidget`/`#removeWidget` rather
/// than through the `GridLayout` tree (see `KubeUIScreen#mouseClicked`) - it needs to render on
/// top of, and be positioned independently of, the screen's normal layout flow.
class KubeUIContextMenuWidget extends AbstractWidget implements KubeUINarratable {
	private static final int ROW_HEIGHT = 14;
	private static final int PADDING = 4;

	private final Font font;
	private final List<String> items;
	private final IntConsumer onSelect;
	private String narration;
	private int highlightedRow = 0;

	KubeUIContextMenuWidget(int x, int y, List<String> items, Font font, IntConsumer onSelect) {
		super(x, y, width(items, font), items.size() * ROW_HEIGHT + PADDING * 2, Component.literal("Context menu"));
		this.items = items;
		this.font = font;
		this.onSelect = onSelect;
	}

	private static int width(List<String> items, Font font) {
		int max = 60;
		for (String item : items) {
			max = Math.max(max, font.width(item) + PADDING * 2);
		}
		return max;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	private int rowAt(double mouseY) {
		return (int) ((mouseY - getY() - PADDING) / ROW_HEIGHT);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		int row = rowAt(event.y());
		if (row >= 0 && row < items.size()) {
			onSelect.accept(row);
		}
	}

	/// Up/Down moves the highlighted row, Enter/Space selects it - this widget is a single
	/// `AbstractWidget` standing in for what's conceptually a list of options, so unlike a real
	/// list of separately-focusable `Button`s, keyboard navigation between "rows" has to be
	/// implemented here by hand rather than inherited from Tab-focus order.
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isUp()) {
			highlightedRow = Math.max(0, highlightedRow - 1);
			return true;
		}
		if (event.isDown()) {
			highlightedRow = Math.min(items.size() - 1, highlightedRow + 1);
			return true;
		}
		if (event.isSelection() && highlightedRow >= 0 && highlightedRow < items.size()) {
			onSelect.accept(highlightedRow);
			return true;
		}
		return false;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xF0202020);
		graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF45D6C9);

		int hoveredRow = isMouseOver(mouseX, mouseY) ? rowAt(mouseY) : highlightedRow;

		for (int i = 0; i < items.size(); i++) {
			int rowY = getY() + PADDING + i * ROW_HEIGHT;
			if (i == hoveredRow) {
				graphics.fill(getX() + 1, rowY, getX() + getWidth() - 1, rowY + ROW_HEIGHT, 0x40FFFFFF);
			}
			graphics.text(font, items.get(i), getX() + PADDING, rowY + 2, 0xFFFFFFFF, false);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : String.join(", ", items)));
	}
}
