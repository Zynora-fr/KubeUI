package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

/// Client-side half of `KubeUI.shareWaypoint(waypointId, targetPlayerName)` - sends
/// the *sender's* own real waypoint data (the server never stores waypoints itself, only forwards
/// them, so it has nothing to look up), and shows a real confirm dialog on the receiving end
/// rather than adding a shared waypoint silently.
final class KubeUIWaypointBridge {
	private KubeUIWaypointBridge() {
	}

	static void share(String waypointId, String targetPlayerName) {
		var waypoint = KubeUIWaypoints.all().stream().filter(w -> w.id().equals(waypointId)).findFirst().orElse(null);
		if (waypoint == null) {
			return;
		}

		var data = new CompoundTag();
		data.putString("targetPlayerName", targetPlayerName);
		data.putString("id", waypoint.id());
		data.putString("name", waypoint.name());
		data.putDouble("x", waypoint.x());
		data.putDouble("y", waypoint.y());
		data.putDouble("z", waypoint.z());
		data.putString("dimension", waypoint.dimension());
		data.putInt("color", waypoint.color());
		KubeUINetworking.sendAction(KubeUIActions.WAYPOINT_SHARE_ACTION, data);
	}

	static void receiveShared(CompoundTag data) {
		String senderName = KubeUINbtCompat.getStringOr(data, "senderName", "Someone");
		var waypoint = new KubeUIWaypoint(
			KubeUINbtCompat.getStringOr(data, "id", java.util.UUID.randomUUID().toString()),
			KubeUINbtCompat.getStringOr(data, "name", "Waypoint"),
			KubeUINbtCompat.getDoubleOr(data, "x", 0), KubeUINbtCompat.getDoubleOr(data, "y", 0), KubeUINbtCompat.getDoubleOr(data, "z", 0),
			KubeUINbtCompat.getStringOr(data, "dimension", "minecraft:overworld"),
			KubeUINbtCompat.getIntOr(data, "color", 0xFFFFD700)
		);

		KubeUIScreenBuilder.confirm(
			"Waypoint shared",
			senderName + " wants to share the waypoint \"" + waypoint.name() + "\" with you. Add it?",
			() -> KubeUIWaypoints.addShared(waypoint),
			() -> {
			}
		);
	}
}
