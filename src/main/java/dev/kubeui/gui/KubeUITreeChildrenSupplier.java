package dev.kubeui.gui;

import java.util.List;

/// Returns `node`'s children for `.tree(id, rootNodes, childrenOf, renderer)` - an empty list
/// means `node` is a leaf (no expand/collapse toggle shown for it).
@FunctionalInterface
public interface KubeUITreeChildrenSupplier {
	List<?> children(Object node);
}
