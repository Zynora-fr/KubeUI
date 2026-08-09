package dev.kubeui.plugin;

import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIStatusHud;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/// A single overlay for every active buff/debuff, "lisible d'un coup d'œil au-delà de l'écran
/// d'effets vanilla par défaut" (real ask: readable at a glance, beyond vanilla's own top-right
/// potion icon row) - real vanilla `MobEffect`s (read live off the local player,
/// `LivingEntity#getActiveEffects()`, no network sync needed at all) and script-defined custom
/// statuses ([KubeUIStatusHud], synced from [dev.kubeui.gui.KubeUIStatusEffects]) drawn as one
/// unified row along the left edge instead of two separate, differently-styled displays. Icons are
/// real item icons (`graphics.item(...)`) when a status's `icon` resolves to a registered item -
/// deliberately not vanilla's own effect-icon texture atlas (its real accessor in this rendering
/// rewrite wasn't independently verified this session, unlike `graphics.item(...)` which is already
/// proven throughout this mod) - falling back to just the name/duration text otherwise.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIStatusOverlayHudRenderer {
	private static final int LEFT_MARGIN = 6;
	private static final int TOP_MARGIN = 40;
	private static final int ROW_HEIGHT = 18;
	private static final int ICON_SIZE = 16;

	private KubeUIStatusOverlayHudRenderer() {
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		var mc = Minecraft.getInstance();
		var player = mc.player;
		if (player == null || mc.options.hideGui) {
			return;
		}

		var graphics = event.getGuiGraphics();
		var font = mc.font;
		int y = TOP_MARGIN;

		for (var effect : player.getActiveEffects()) {
			String name = effect.getEffect().value().getDisplayName().getString();
			String durationText = effect.getDuration() < 0 ? "∞" : (effect.getDuration() / 20) + "s";
			drawRow(graphics, font, LEFT_MARGIN, y, effect.getEffect().value().getColor() | 0xFF000000, null, name, durationText);
			y += ROW_HEIGHT;
		}

		for (var status : KubeUIStatusHud.active()) {
			ItemStack icon = ItemStack.EMPTY;
			var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(status.icon()));
			if (item != null) {
				icon = new ItemStack(item);
			}
			String durationText = status.remaining() < 0 ? "∞" : (status.remaining() / 20) + "s";
			drawRow(graphics, font, LEFT_MARGIN, y, 0xFF9060E0, icon, status.name(), durationText);
			y += ROW_HEIGHT;
		}
	}

	private static void drawRow(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int x, int y, int accentColor, ItemStack icon, String name, String durationText) {
		String label = name + " (" + durationText + ")";
		int boxWidth = ICON_SIZE + 4 + font.width(label) + 6;
		graphics.fill(x, y, x + boxWidth, y + ROW_HEIGHT, 0xB0101010);
		graphics.fill(x, y, x + 2, y + ROW_HEIGHT, accentColor);

		if (icon != null && !icon.isEmpty()) {
			graphics.item(icon, x + 3, y + 1);
		} else {
			graphics.fill(x + 4, y + 1, x + 4 + ICON_SIZE, y + 1 + ICON_SIZE, accentColor);
		}

		graphics.text(font, label, x + ICON_SIZE + 6, y + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF, true);
	}
}
