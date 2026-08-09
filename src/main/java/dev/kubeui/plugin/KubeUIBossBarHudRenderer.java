package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIBossBarHud;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// Draws every bar [KubeUIBossBarHud] currently holds, top-center, stacked top-to-bottom -
/// deliberately not reusing vanilla's own `BossHealthOverlay` (real, but tied to real
/// `LerpingBossEvent`s the server would need to fake registering just to reuse it, and offers no
/// hook for a script-defined phase color/text change at a health threshold). Draws nothing when
/// nothing's active, same idle behavior every other HUD renderer in this mod has.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIBossBarHudRenderer {
	private static final int BAR_WIDTH = 180;
	/// Shrunk from 8 ("la barre boss est trop haute" - real, reported feedback) - vanilla's own boss
	/// bar sprite is a similarly thin strip; the label carries the readability, not bar thickness.
	private static final int BAR_HEIGHT = 5;
	private static final int GAP = 3;
	private static final int TOP_MARGIN = 4;

	private KubeUIBossBarHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}

		var graphics = event.getGuiGraphics();
		var font = mc.font;
		int centerX = graphics.guiWidth() / 2;
		int y = TOP_MARGIN;

		for (var bar : KubeUIBossBarHud.active()) {
			int x = centerX - BAR_WIDTH / 2;

			graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF202020);
			graphics.outline(x - 1, y - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2, 0xFF000000);
			float fraction = bar.maxHealth() > 0 ? Math.max(0, Math.min(1, bar.health() / bar.maxHealth())) : 0;
			int filled = (int) (BAR_WIDTH * fraction);
			if (filled > 0) {
				graphics.fill(x, y, x + filled, y + BAR_HEIGHT, bar.color());
			}

			String label = bar.phaseText().isEmpty() ? bar.name() : bar.name() + " - " + bar.phaseText();
			graphics.centeredText(font, label, centerX, y - font.lineHeight, 0xFFFFFFFF);

			y += BAR_HEIGHT + GAP + font.lineHeight;
		}
	}
}
