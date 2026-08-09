package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIScreenBuilder;
import dev.kubeui.gui.KubeUIWaypoints;
import dev.kubeui.gui.KubeUIWorldMapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/// `/kubeui map open` (see [KubeUIScreenBuilder#worldMap]) and `/kubeui map export` -
/// the same real `Screenshot.grab` mechanism `/kubeui screenshot` ([KubeUIDebugCommands]) already
/// uses, just requiring the world map screen to actually be the one currently open (grabbing
/// "a screenshot" only means "an image of the map" if that's genuinely what's on screen).
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIMapCommands {
	private KubeUIMapCommands() {
	}

	@SubscribeEvent
	static void register(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("kubeui")
				.then(Commands.literal("map")
					.then(Commands.literal("open").executes(ctx -> open(ctx.getSource())))
					.then(Commands.literal("export").executes(ctx -> export(ctx.getSource())))
					.then(Commands.literal("waypoints").executes(ctx -> listWaypoints(ctx.getSource()))))
		);
	}

	private static int listWaypoints(CommandSourceStack source) {
		var waypoints = KubeUIWaypoints.all();
		if (waypoints.isEmpty()) {
			source.sendSystemMessage(Component.literal("No waypoints yet - open the map and click \"Add Waypoint Here\"."));
			return 1;
		}
		for (var waypoint : waypoints) {
			source.sendSystemMessage(Component.literal(waypoint.name() + " - id: " + waypoint.id() + " (" + (int) waypoint.x() + ", " + (int) waypoint.y() + ", " + (int) waypoint.z() + ")"));
		}
		return 1;
	}

	private static int open(CommandSourceStack source) {
		KubeUIScreenBuilder.worldMap();
		return 1;
	}

	private static int export(CommandSourceStack source) {
		var mc = Minecraft.getInstance();
		if (!(mc.screen instanceof KubeUIWorldMapScreen)) {
			source.sendSystemMessage(Component.literal("Open the world map first (/kubeui map open) before exporting it."));
			return 0;
		}
		Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), component -> mc.execute(() -> source.sendSystemMessage(component)));
		return 1;
	}
}
