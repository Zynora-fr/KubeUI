package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIToast;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// Draws `KubeUIToast`'s active entries every frame via NeoForge's `RenderGuiEvent.Post` - the
/// HUD layer, not a KubeUIScreen, so toasts show up (and stack, and expire) independently of
/// whatever screen is or isn't currently open.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIToastRenderer {
	private static final int MARGIN = 8;
	private static final int PADDING_X = 8;
	private static final int GAP = 4;

	private KubeUIToastRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var toasts = KubeUIToast.active();
		if (toasts.isEmpty()) {
			return;
		}

		var mc = Minecraft.getInstance();
		var graphics = event.getGuiGraphics();
		var font = mc.font;
		int boxHeight = font.lineHeight + 8;
		int y = MARGIN;

		for (var toast : toasts) {
			int textWidth = font.width(toast.message());
			int boxWidth = textWidth + PADDING_X * 2;
			int x = graphics.guiWidth() - MARGIN - boxWidth;

			graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xE0202020);
			graphics.renderOutline(x, y, boxWidth, boxHeight, 0xFF45D6C9);
			graphics.drawString(font, toast.message(), x + PADDING_X, y + (boxHeight - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);

			y += boxHeight + GAP;
		}
	}
}
