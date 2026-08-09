package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUICombatLogHud;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// A small "recent hits" log, bottom-right - "indicateur de dégâts... activable/désactivable par
/// joueur" (see [dev.kubeui.gui.KubeUIActions#setCombatLogEnabled]) scoped down to a real combat-log
/// list instead of world-space floating numbers over each entity (see [KubeUICombatLogHud]'s own
/// doc for why). Fades out [#VISIBLE_MS] after the last update rather than sitting on screen
/// forever once a fight's over.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUICombatLogHudRenderer {
	private static final long VISIBLE_MS = 6000;
	private static final int RIGHT_MARGIN = 6;
	private static final int BOTTOM_MARGIN = 80;
	private static final int LINE_HEIGHT = 10;

	private KubeUICombatLogHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) {
			return;
		}
		var lines = KubeUICombatLogHud.lines();
		if (lines.isEmpty() || System.currentTimeMillis() - KubeUICombatLogHud.lastUpdateAt() > VISIBLE_MS) {
			return;
		}

		var graphics = new dev.kubeui.gui.GuiGraphicsExtractor(event.getGuiGraphics());
		var font = mc.font;
		int y = graphics.guiHeight() - BOTTOM_MARGIN - lines.size() * LINE_HEIGHT;

		for (var line : lines) {
			int x = graphics.guiWidth() - RIGHT_MARGIN - font.width(line);
			graphics.text(font, line, x, y, 0xFFFFFFFF, true);
			y += LINE_HEIGHT;
		}
	}
}
