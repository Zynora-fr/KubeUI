package dev.kubeui;

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
	}
}
