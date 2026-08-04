package dev.kubeui.gui;

import java.util.ArrayList;
import java.util.List;

/// Shared by every [KubeUIListDragHandle] of the same `.reorderableList(...)` - one instance per
/// list, created when the list is built and referenced by every row's handle, so a drag on one
/// handle can find and compare against every other row's actual on-screen position.
/// `draggingIndex` is the dragged item's *current* display position (updated live as the drag
/// crosses into another row, which is also what physically moves the rows - see
/// `KubeUIListDragHandle#onDrag`); `dragStartIndex` is where it was when the drag began, kept
/// around to report a meaningful `onReorder(screen, from, to)` once the drag ends.
final class KubeUIListDragState {
	final List<KubeUIListDragHandle> handles = new ArrayList<>();
	boolean dragging;
	int draggingIndex = -1;
	int dragStartIndex = -1;
}
