package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUICommandRegistry;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/// Registers every command a script asked for via `KubeUI.registerCommand(name, callback)` -
/// separate from the mod's own built-in `/kubeui ...` commands in [KubeUIDebugCommands].
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIScriptCommands {
	private KubeUIScriptCommands() {
	}

	@SubscribeEvent
	static void register(RegisterClientCommandsEvent event) {
		for (var entry : KubeUICommandRegistry.all().entrySet()) {
			Runnable callback = entry.getValue();
			if (callback == null) {
				continue;
			}
			event.getDispatcher().register(
				Commands.literal(entry.getKey()).executes(ctx -> {
					callback.run();
					return 1;
				})
			);
		}
	}
}
