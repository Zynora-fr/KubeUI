package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIQuestHud;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// Draws whichever quest [KubeUIQuestHud] currently holds (pushed periodically by
/// [dev.kubeui.gui.KubeUIQuestEvents]) every frame via `RenderGuiEvent.Post` - top-left, so it
/// never collides with `KubeUIToastRenderer`'s top-right stack. Draws nothing when no quest is
/// tracked (`KubeUIQuestHud#title()` is `null`), same "just don't draw" idle behavior
/// `KubeUIToastRenderer` has when its list is empty.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIQuestHudRenderer {
	private static final int MARGIN = 8;
	private static final int PADDING = 6;
	private static final int LINE_GAP = 2;

	private KubeUIQuestHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		String title = KubeUIQuestHud.title();
		if (title == null) {
			return;
		}

		var mc = Minecraft.getInstance();
		var graphics = event.getGuiGraphics();
		var font = mc.font;
		var objectives = KubeUIQuestHud.objectives();
		boolean canComplete = KubeUIQuestHud.canComplete();

		int lineHeight = font.lineHeight + LINE_GAP;
		int lineCount = 1 + objectives.size() + (canComplete ? 1 : 0);
		int boxWidth = font.width(title) + PADDING * 2;
		for (var objective : objectives) {
			boxWidth = Math.max(boxWidth, font.width(objectiveLine(objective)) + PADDING * 2);
		}
		if (canComplete) {
			boxWidth = Math.max(boxWidth, font.width(READY_TEXT) + PADDING * 2);
		}
		int boxHeight = lineCount * lineHeight + PADDING * 2 - LINE_GAP;

		int x = MARGIN;
		int y = MARGIN;
		graphics.fill(x, y, x + boxWidth, y + boxHeight, 0xC0202020);
		graphics.renderOutline(x, y, boxWidth, boxHeight, 0xFF45D6C9);

		int textY = y + PADDING;
		graphics.drawString(font, title, x + PADDING, textY, 0xFFFFD700, false);
		textY += lineHeight;

		for (var objective : objectives) {
			int color = objective.progress() >= objective.target() ? 0xFF55FF55 : 0xFFDDDDDD;
			graphics.drawString(font, objectiveLine(objective), x + PADDING, textY, color, false);
			textY += lineHeight;
		}

		if (canComplete) {
			graphics.drawString(font, READY_TEXT, x + PADDING, textY, 0xFF55FF55, false);
		}
	}

	private static final String READY_TEXT = "Ready to turn in!";

	private static String objectiveLine(KubeUIQuestHud.Entry objective) {
		return objective.label() + ": " + objective.progress() + "/" + objective.target();
	}
}
