package dev.kubeui.gui;

/// Fires once a `.reorderableList(...)` drag-handle is released over a different row than it
/// started on. `fromIndex`/`toIndex` are indices into the `items` list that list was built from -
/// reordering the underlying data (and showing the new order) is the script's own job, typically
/// via `screen.update(b -> { b.remove(id); b.reorderableList(id, newItems, renderer, onReorder); })`.
@FunctionalInterface
public interface KubeUIListReorderListener {
	void onReorder(KubeUIContext screen, int fromIndex, int toIndex);
}
