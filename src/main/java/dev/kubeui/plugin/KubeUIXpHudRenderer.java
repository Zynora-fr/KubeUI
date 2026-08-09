package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// A permanent HUD level/XP bar - reuses the same real per-frame `RenderGuiEvent.Post`
/// mechanism [KubeUIQuestHudRenderer]/`KubeUIToastRenderer` already use, not the transient
/// [dev.kubeui.gui.KubeUIToast] itself (that auto-dismisses, the opposite of "permanent"). No
/// network round trip/data-cache class needed unlike those two - level/XP progress are already
/// real fields on the local player (`Player#experienceLevel`/`#experienceProgress`), readable
/// directly every frame.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIXpHudRenderer {
	private static final int BAR_WIDTH = 120;
	private static final int BAR_HEIGHT = 5;
	private static final int MARGIN_BOTTOM = 28;

	private KubeUIXpHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		var player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}

		var graphics = new dev.kubeui.gui.GuiGraphicsExtractor(event.getGuiGraphics());
		int x = (graphics.guiWidth() - BAR_WIDTH) / 2;
		int y = graphics.guiHeight() - MARGIN_BOTTOM;

		graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF303030);
		int filled = (int) (BAR_WIDTH * Math.max(0.0F, Math.min(1.0F, player.experienceProgress)));
		if (filled > 0) {
			graphics.fill(x, y, x + filled, y + BAR_HEIGHT, 0xFF6AEE8C);
		}

		String levelText = "Lvl " + player.experienceLevel;
		graphics.text(mc.font, levelText, x + BAR_WIDTH / 2 - mc.font.width(levelText) / 2, y - mc.font.lineHeight - 1, 0xFF6AEE8C, true);
	}
}
