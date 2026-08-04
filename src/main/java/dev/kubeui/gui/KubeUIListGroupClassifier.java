package dev.kubeui.gui;

/// Returns the section-header text `item` belongs under, for `.groupedList(...)`. Items are
/// assumed to already come in group-contiguous order (a header is inserted whenever this returns
/// something different from the previous item's group, not by re-sorting `items`).
@FunctionalInterface
public interface KubeUIListGroupClassifier {
	String groupOf(Object item);
}
