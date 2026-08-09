package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/// A screen-space attack-range/AOE indicator (`KubeUIActions.showAoeIndicator(player, radius,
/// color)`/`.hideAoeIndicator(player)`) - "cercle/zone dessinée en HUD... avant validation du clic"
/// (real ask: shown before the player commits to a targeted ability). Deliberately a HUD-space ring
/// around the crosshair rather than a real ground-projected circle in the 3D world (the latter needs
/// real terrain-following world-space rendering this session has no way to visually verify) - an
/// honest, reduced scope, same kind of trade-off already made for [KubeUICombatLog]'s own
/// floating-damage-indicator scope-down.
final class KubeUIAoeIndicator {
	private KubeUIAoeIndicator() {
	}

	static void show(ServerPlayer player, float radiusBlocks, int color) {
		var data = new CompoundTag();
		data.putBoolean("visible", true);
		data.putFloat("radius", radiusBlocks);
		data.putInt("color", color);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.AOE_INDICATOR_UPDATE_SCREEN_ID, data));
	}

	static void hide(ServerPlayer player) {
		var data = new CompoundTag();
		data.putBoolean("visible", false);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.AOE_INDICATOR_UPDATE_SCREEN_ID, data));
	}
}
