package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIHud;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// Draws every bar/label [KubeUIHud] currently holds - the real, general "build your own HUD
/// overlay in JS" renderer this mod was missing (every *other* HUD element - boss bar, combat log,
/// waypoint tracker, ... - is hardcoded Java with no script control over its look at all). Same
/// idle-when-empty behavior every other HUD renderer in this mod already has.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIHudRenderer {
	private KubeUIHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}

		var graphics = event.getGuiGraphics();
		var font = mc.font;
		int screenW = graphics.guiWidth();
		int screenH = graphics.guiHeight();

		for (var bar : KubeUIHud.bars()) {
			int[] pos = resolveAnchor(bar.anchor(), bar.x(), bar.y(), bar.width(), bar.height(), screenW, screenH);
			int x = pos[0], y = pos[1];

			graphics.fill(x, y, x + bar.width(), y + bar.height(), bar.bgColor());
			graphics.outline(x - 1, y - 1, bar.width() + 2, bar.height() + 2, bar.borderColor());
			double fraction = bar.max() > 0 ? Math.max(0, Math.min(1, bar.value() / bar.max())) : 0;
			int filled = (int) (bar.width() * fraction);
			if (filled > 0) {
				graphics.fill(x, y, x + filled, y + bar.height(), bar.barColor());
			}
			if (!bar.label().isEmpty()) {
				graphics.centeredText(font, bar.label(), x + bar.width() / 2, y - font.lineHeight - 1, bar.labelColor());
			}
		}

		for (var label : KubeUIHud.labels()) {
			int[] pos = resolveAnchor(label.anchor(), label.x(), label.y(), 0, 0, screenW, screenH);
			if (label.centered()) {
				graphics.centeredText(font, label.text(), pos[0], pos[1], label.color());
			} else {
				graphics.text(font, label.text(), pos[0], pos[1], label.color(), label.shadow());
			}
		}
	}

	/// Resolves `anchor` (`"topLeft"` (default, unrecognized names fall back to it)/`"topCenter"`/
	/// `"topRight"`/`"bottomLeft"`/`"bottomCenter"`/`"bottomRight"`) plus a pixel offset into an
	/// absolute screen position, `elemWidth`/`elemHeight` only mattering for the *Center*/*Right*/
	/// *Bottom* anchors (subtracted so the element's far edge lands at the true edge, not its
	/// origin) - `0`/`0` is the right call for a label, which has no fixed footprint of its own.
	private static int[] resolveAnchor(String anchor, int x, int y, int elemWidth, int elemHeight, int screenW, int screenH) {
		int baseX = 0, baseY = 0;
		switch (anchor) {
			case "topCenter" -> baseX = screenW / 2 - elemWidth / 2;
			case "topRight" -> baseX = screenW - elemWidth;
			case "bottomLeft" -> baseY = screenH - elemHeight;
			case "bottomCenter" -> {
				baseX = screenW / 2 - elemWidth / 2;
				baseY = screenH - elemHeight;
			}
			case "bottomRight" -> {
				baseX = screenW - elemWidth;
				baseY = screenH - elemHeight;
			}
			default -> {
			}
		}
		return new int[]{baseX + x, baseY + y};
	}
}
