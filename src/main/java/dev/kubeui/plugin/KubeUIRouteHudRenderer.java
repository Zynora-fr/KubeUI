package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIWaypoints;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// A "route tracking" HUD overlay - a directional indicator (one of 8 real compass
/// glyphs, picked from the real bearing-to-waypoint minus the player's own real yaw, not a rotated
/// sprite - simpler and avoids a pose-transform this session can't visually verify) plus distance,
/// for whichever waypoint [KubeUIWaypointHud] currently reports as tracked
/// ([dev.kubeui.gui.KubeUIWaypoints#setTracked]). Same real per-frame `RenderGuiEvent.Post`
/// mechanism as [KubeUIXpHudRenderer]/[KubeUIQuestHudRenderer], positioned top-center so it
/// doesn't collide with either.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIRouteHudRenderer {
	private static final String[] ARROWS = {"^", "/^", ">", "\\v", "v", "v/", "<", "^\\"};

	private KubeUIRouteHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		var player = mc.player;
		var waypoint = KubeUIWaypoints.tracked();
		if (player == null || waypoint == null || !waypoint.dimension().equals(mc.level.dimension().identifier().toString())) {
			return;
		}

		double dx = waypoint.x() - player.getX();
		double dz = waypoint.z() - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);

		double bearing = Math.toDegrees(Math.atan2(dx, -dz));
		double relative = net.minecraft.util.Mth.wrapDegrees(bearing - player.getYRot());
		int index = Math.floorMod((int) Math.round(relative / 45.0), 8);

		String text = ARROWS[index] + " " + waypoint.name() + " (" + Math.round(distance) + "m)";
		var graphics = event.getGuiGraphics();
		int x = graphics.guiWidth() / 2 - mc.font.width(text) / 2;
		graphics.fill(x - 4, 4, x + mc.font.width(text) + 4, 4 + mc.font.lineHeight + 4, 0xA0202020);
		graphics.text(mc.font, text, x, 6, 0xFFFFD700, true);
	}
}
