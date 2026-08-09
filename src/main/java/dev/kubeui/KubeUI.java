package dev.kubeui;

import dev.kubeui.gui.KubeUIBlockEntities;
import dev.kubeui.gui.KubeUIBlocks;
import dev.kubeui.gui.KubeUIConfig;
import dev.kubeui.gui.KubeUIItems;
import dev.kubeui.gui.KubeUIMenus;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(KubeUI.MOD_ID)
public class KubeUI {
	public static final String MOD_ID = "kubeui";
	public static final Logger LOGGER = LoggerFactory.getLogger("KubeUI");

	public KubeUI(IEventBus modEventBus, ModContainer modContainer) {
		LOGGER.info("KubeUI loaded - GUI bindings are provided to KubeJS scripts via KubeUIPlugin");
		KubeUIConfig.register(modContainer);
		KubeUIMenus.register(modEventBus);
		KubeUIItems.register(modEventBus);
		KubeUIBlocks.register(modEventBus);
		KubeUIBlockEntities.register(modEventBus);
	}
}
