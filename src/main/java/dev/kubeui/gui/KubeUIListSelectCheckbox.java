package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/// The selection box at the start of a `.selectableList(...)` row - plain click selects only this
/// row, ctrl-click toggles it without touching the rest, shift-click selects the whole range since
/// the last click (same modifier conventions as vanilla's creative inventory/advancement screens).
class KubeUIListSelectCheckbox extends AbstractWidget implements KubeUINarratable {
	private final DoubleClickTracker doubleClickTracker = new DoubleClickTracker();
	private static final int SIZE = 10;

	private final int index;
	private final KubeUIListSelectionState state;
	private final KubeUIContext context;
	private final BiConsumer<KubeUIContext, List<Integer>> onSelectionChange;
	private String narration;

	KubeUIListSelectCheckbox(int x, int y, int index, KubeUIListSelectionState state, KubeUIContext context, BiConsumer<KubeUIContext, List<Integer>> onSelectionChange) {
		super(x, y, SIZE, SIZE, Component.literal("Select"));
		this.index = index;
		this.state = state;
		this.context = context;
		this.onSelectionChange = onSelectionChange;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	public void onClick(double mouseX, double mouseY, int button) {
		onClick(new MouseButtonEvent(mouseX, mouseY, button), doubleClickTracker.registerClick(mouseX, mouseY));
	}

	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		toggle(event.hasShiftDown(), event.hasControlDown());
	}

	/// Enter/Space toggles this row - added alongside the new focus outline ([KubeUIFocusOutline])
	/// since a widget a keyboard user can now clearly see is focused should also be operable by
	/// keyboard, not just visually highlighted. Shift/Ctrl work the same as their mouse-click
	/// equivalents (see [#onClick]) since [KeyEvent] exposes the same modifier accessors.
	@Override
	public boolean keyPressed(int keyCode, int scancode, int modifiers) {
		return keyPressed(new KeyEvent(keyCode, scancode, modifiers));
	}

	public boolean keyPressed(KeyEvent event) {
		if (!event.isSelection()) {
			return false;
		}
		toggle(event.hasShiftDown(), event.hasControlDown());
		return true;
	}

	private void toggle(boolean shift, boolean control) {
		if (shift && state.lastToggled != null) {
			int from = Math.min(state.lastToggled, index);
			int to = Math.max(state.lastToggled, index);
			for (int i = from; i <= to; i++) {
				state.selected.add(i);
			}
		} else if (control) {
			if (!state.selected.add(index)) {
				state.selected.remove(index);
			}
		} else {
			state.selected.clear();
			state.selected.add(index);
		}

		state.lastToggled = index;

		if (onSelectionChange != null) {
			var sorted = new ArrayList<>(state.selected);
			Collections.sort(sorted);
			onSelectionChange.accept(context, sorted);
		}
	}

	@Override
	protected void renderWidget(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		extractWidgetRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean checked = state.selected.contains(index);
		graphics.outline(getX(), getY(), SIZE, SIZE, isHovered() ? 0xFFEAF3F3 : 0xFF6B7679);
		if (checked) {
			graphics.fill(getX() + 2, getY() + 2, getX() + SIZE - 2, getY() + SIZE - 2, KubeUITheme.accentColor());
		}
		KubeUIFocusOutline.draw(graphics, this);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		String defaultText = (state.selected.contains(index) ? "Selected" : "Not selected") + ", row " + (index + 1);
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : defaultText));
	}
}
