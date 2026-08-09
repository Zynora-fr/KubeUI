package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/// Machine alerts (breakdown/full queue/out of resources) - reuses the real
/// [KubeUIToast] system already delivered, just triggered server-side
/// (a toast is normally a purely client-side call, `KubeUI.toast(...)`) via one more reserved
/// `KubeUIRemotePayload` screenId, same convention as every other server->client push in this mod.
final class KubeUIMachineAlerts {
	private static final int DURATION_MS = 5000;

	private KubeUIMachineAlerts() {
	}

	static void send(ServerPlayer player, String message) {
		var data = new CompoundTag();
		data.putString("message", message);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.MACHINE_ALERT_SCREEN_ID, data));
	}

	static void receive(CompoundTag data) {
		KubeUIToast.add(KubeUINbtCompat.getStringOr(data, "message", ""), DURATION_MS);
	}
}
