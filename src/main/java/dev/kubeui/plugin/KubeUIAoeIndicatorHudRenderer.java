package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIAoeIndicatorHud;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// A ring of dots centered on the crosshair, radius scaled from real blocks
/// ([#PIXELS_PER_BLOCK]) - see [dev.kubeui.gui.KubeUIAoeIndicator]'s own doc for why this is
/// HUD-space rather than a real ground-projected circle. Drawn from plain `fill()` dots (a
/// `RenderGuiEvent.Post` HUD layer has no cheap native circle-outline primitive), same "safe
/// primitives only" approach [KubeUIMachineScreen]'s own hand-drawn flame icon already uses.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIAoeIndicatorHudRenderer {
	private static final int PIXELS_PER_BLOCK = 8;
	private static final int DOT_COUNT = 32;

	private KubeUIAoeIndicatorHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !KubeUIAoeIndicatorHud.visible()) {
			return;
		}

		var graphics = new dev.kubeui.gui.GuiGraphicsExtractor(event.getGuiGraphics());
		int centerX = graphics.guiWidth() / 2;
		int centerY = graphics.guiHeight() / 2;
		int radiusPx = Math.round(KubeUIAoeIndicatorHud.radius() * PIXELS_PER_BLOCK);
		int color = KubeUIAoeIndicatorHud.color();

		for (int i = 0; i < DOT_COUNT; i++) {
			double angle = 2 * Math.PI * i / DOT_COUNT;
			int x = centerX + (int) Math.round(Math.cos(angle) * radiusPx);
			int y = centerY + (int) Math.round(Math.sin(angle) * radiusPx);
			graphics.fill(x - 1, y - 1, x + 1, y + 1, color);
		}
	}
}
