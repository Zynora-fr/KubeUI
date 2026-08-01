package dev.kubeui.plugin;

import com.mojang.brigadier.arguments.FloatArgumentType;
import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIDebug;
import dev.kubeui.gui.KubeUIScreenBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/// `/kubeui debug` (report on the most recently opened KubeUI screen - a chat command can't
/// inspect the *currently* open one, since typing it closes that screen first),
/// `/kubeui outline` (toggle widget bounding-box outlines), `/kubeui screenshot` and
/// `/kubeui scale [factor]` (shrink/grow every KubeUI screen - see [KubeUIScreenBuilder#setScale]).
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIDebugCommands {
	private KubeUIDebugCommands() {
	}

	@SubscribeEvent
	static void register(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("kubeui")
				.then(Commands.literal("debug").executes(ctx -> debug(ctx.getSource())))
				.then(Commands.literal("outline").executes(ctx -> outline(ctx.getSource())))
				.then(Commands.literal("screenshot").executes(ctx -> screenshot(ctx.getSource())))
				.then(Commands.literal("scale")
					.executes(ctx -> scaleStatus(ctx.getSource()))
					.then(Commands.argument("factor", FloatArgumentType.floatArg(0.5f, 2.0f))
						.executes(ctx -> scaleSet(ctx.getSource(), FloatArgumentType.getFloat(ctx, "factor")))))
		);
	}

	private static int debug(CommandSourceStack source) {
		var summary = KubeUIDebug.lastOpenedSummary();

		if (summary == null) {
			source.sendSystemMessage(Component.literal("No KubeUI screen has been opened yet this session."));
			return 0;
		}

		source.sendSystemMessage(Component.literal("Last KubeUI screen:\n" + summary));
		return 1;
	}

	private static int outline(CommandSourceStack source) {
		boolean enabled = !KubeUIDebug.isOutlineEnabled();
		KubeUIDebug.setOutlineEnabled(enabled);
		source.sendSystemMessage(Component.literal("KubeUI widget outlines: " + (enabled ? "ON" : "OFF")));
		return 1;
	}

	private static int screenshot(CommandSourceStack source) {
		var mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), component -> mc.execute(() -> source.sendSystemMessage(component)));
		return 1;
	}

	private static int scaleStatus(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal(
			"KubeUI scale: " + KubeUIScreenBuilder.getScale() + " (0.5-2.0). Set with /kubeui scale <factor>."
		));
		return 1;
	}

	private static int scaleSet(CommandSourceStack source, float factor) {
		KubeUIScreenBuilder.setScale(factor);
		source.sendSystemMessage(Component.literal(
			"KubeUI scale set to " + KubeUIScreenBuilder.getScale() + " - already-open screens need to be closed and reopened to pick it up."
		));
		return 1;
	}
}
