package dev.kubeui.gui;

import java.util.LinkedHashSet;
import java.util.Set;

/// Shared by every [KubeUIListSelectCheckbox] of the same `.selectableList(...)` - one instance
/// per list, read by `KubeUIContext#getSelectedListItems`. `lastToggled` backs shift-click range
/// selection (selects/deselects everything between it and whatever was just shift-clicked).
final class KubeUIListSelectionState {
	final Set<Integer> selected = new LinkedHashSet<>();
	Integer lastToggled;
}
