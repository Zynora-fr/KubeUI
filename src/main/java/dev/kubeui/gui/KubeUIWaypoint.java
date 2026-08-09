package dev.kubeui.gui;

/// One waypoint - `dimension` is the real dimension id string (e.g.
/// `"minecraft:overworld"`), so a waypoint only ever shows on [KubeUIWorldMapScreen] while that
/// dimension is the one being viewed.
public record KubeUIWaypoint(String id, String name, double x, double y, double z, String dimension, int color) {
}
