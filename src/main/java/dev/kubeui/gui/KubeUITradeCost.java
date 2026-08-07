package dev.kubeui.gui;

/// One cost entry (item id + count) a player must have and hand over to complete a
/// [KubeUITradeDef].
record KubeUITradeCost(String item, int count) {
}
