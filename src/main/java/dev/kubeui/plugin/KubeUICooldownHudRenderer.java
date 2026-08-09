package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUICooldownHud;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// A row of cooldown bars just above the hotbar, one per active [KubeUICooldownHud] entry - "en
/// HUD, cohérente avec un futur système de compétences actives" (real ask: consistent, ready for a
/// future active-skill bar rather than a one-off widget). Draws nothing when nothing's on cooldown.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUICooldownHudRenderer {
	private static final int BAR_WIDTH = 60;
	private static final int BAR_HEIGHT = 4;
	private static final int GAP = 4;
	private static final int BOTTOM_MARGIN = 62;

	private KubeUICooldownHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		var active = KubeUICooldownHud.active();
		if (active.isEmpty()) {
			return;
		}

		var graphics = new dev.kubeui.gui.GuiGraphicsExtractor(event.getGuiGraphics());
		var font = mc.font;
		int totalWidth = active.size() * BAR_WIDTH + (active.size() - 1) * GAP;
		int x = graphics.guiWidth() / 2 - totalWidth / 2;
		int y = graphics.guiHeight() - BOTTOM_MARGIN;

		for (var cooldown : active) {
			graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF202020);
			float fraction = (float) cooldown.remaining() / cooldown.total();
			int filled = (int) (BAR_WIDTH * Math.max(0, Math.min(1, fraction)));
			if (filled > 0) {
				graphics.fill(x, y, x + filled, y + BAR_HEIGHT, 0xFF4090E0);
			}
			String label = cooldown.remaining() >= 20 ? (cooldown.remaining() / 20) + "s" : cooldown.id();
			graphics.centeredText(font, label, x + BAR_WIDTH / 2, y - font.lineHeight - 1, 0xFFCCCCCC);
			x += BAR_WIDTH + GAP;
		}
	}
}
