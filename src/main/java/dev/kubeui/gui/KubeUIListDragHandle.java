package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/// The small drag grip at the start of a `.reorderableList(...)` row. Registers itself into the
/// list's shared [KubeUIListDragState] at construction so a drag started on any one handle can
/// find and compare against every other row's actual on-screen position - no assumption about
/// uniform row height, since it reads the other handles' real `getY()`/`getHeight()` live.
/// Drawn as three filled bars via `fill()` rather than a text glyph (an earlier version used
/// `"≡"`, which isn't in Minecraft's default font and rendered as nothing - an invisible click
/// target is as good as no click target at all).
///
/// Dragging past another row's handle immediately swaps them in [KubeUIScreenBuilder.ReorderableListElement#order]
/// and triggers a rebuild, the same trick `.tree(...)`'s expand/collapse toggle uses
/// (`KubeUIContext#update` with a no-op mutator) - the rows genuinely move as you drag, not just
/// on drop. That rebuild replaces every handle instance (including this one, mid-drag): the old
/// instance stops being drawn but keeps receiving the mouse events for the rest of the drag (the
/// screen's focus reference doesn't change), and keeps working correctly since all of its state
/// lives in `state`/`listElement`, not in itself.
class KubeUIListDragHandle extends AbstractWidget implements KubeUINarratable {
	private final DoubleClickTracker doubleClickTracker = new DoubleClickTracker();
	private static final int SIZE = 16;
	private static final int BAR_HEIGHT = 2;
	private static final int BAR_GAP = 3;

	private final int pos;
	private final KubeUIListDragState state;
	private final KubeUIScreenBuilder.ReorderableListElement listElement;
	private final KubeUIContext context;
	private String narration;

	KubeUIListDragHandle(int x, int y, int pos, KubeUIListDragState state, KubeUIScreenBuilder.ReorderableListElement listElement, KubeUIContext context) {
		super(x, y, SIZE, SIZE, Component.literal("Drag to reorder"));
		this.pos = pos;
		this.state = state;
		this.listElement = listElement;
		this.context = context;
		// A rebuild not caused by this same list (e.g. another widget's `screen.update(...)`)
		// still reconstructs this handle from the same persistent `state` - drop whatever stale
		// instance was previously registered for this position instead of piling up on top of it.
		state.handles.removeIf(h -> h.pos == pos);
		state.handles.add(this);
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
		state.dragging = true;
		state.draggingIndex = pos;
		state.dragStartIndex = pos;
	}

	@Override
	protected void onDrag(double mouseX, double mouseY, double dx, double dy) {
		onDrag(new MouseButtonEvent(mouseX, mouseY, 0), dx, dy);
	}

	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (!state.dragging) {
			return;
		}

		KubeUIListDragHandle target = null;
		for (var handle : state.handles) {
			if (handle.pos != state.draggingIndex && event.y() >= handle.getY() && event.y() < handle.getY() + handle.getHeight()) {
				target = handle;
				break;
			}
		}

		if (target != null) {
			int draggedItemIndex = listElement.order.remove(state.draggingIndex);
			listElement.order.add(target.pos, draggedItemIndex);
			state.draggingIndex = target.pos;
			context.update(b -> {
			});
		}
	}

	@Override
	public void onRelease(double mouseX, double mouseY) {
		onRelease(new MouseButtonEvent(mouseX, mouseY, 0));
	}

	public void onRelease(MouseButtonEvent event) {
		if (state.dragging && state.draggingIndex != state.dragStartIndex && listElement.onReorder != null) {
			listElement.onReorder.onReorder(context, state.dragStartIndex, state.draggingIndex);
		}
		state.dragging = false;
		state.draggingIndex = -1;
		state.dragStartIndex = -1;
	}

	@Override
	protected void renderWidget(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		extractWidgetRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		boolean isDragged = state.dragging && state.draggingIndex == pos;
		int color = isDragged ? 0xFF45D6C9 : (isHovered() ? 0xFFEAF3F3 : 0xFF6B7679);

		int barWidth = getWidth() - 4;
		int barX = getX() + 2;
		int firstBarY = getY() + (getHeight() - (BAR_HEIGHT * 3 + BAR_GAP * 2)) / 2;

		for (int i = 0; i < 3; i++) {
			int barY = firstBarY + i * (BAR_HEIGHT + BAR_GAP);
			graphics.fill(barX, barY, barX + barWidth, barY + BAR_HEIGHT, color);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : "Drag to reorder, row " + (pos + 1)));
	}
}
