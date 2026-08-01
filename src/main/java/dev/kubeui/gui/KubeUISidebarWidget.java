package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/// One button in the Paladium-style icon bar next to the survival inventory (see
/// [KubeUISidebar]/[KubeUISidebarInjector]). Square, with a dark slot-like background, an item or
/// custom texture centered on top, and a hover highlight - clicking runs the script's callback
/// (already safety-wrapped by [KubeUISidebar] before it ever reaches here).
class KubeUISidebarWidget extends AbstractWidget {
	static final int SIZE = 20;
	private static final int ITEM_INSET = 2; // items render at a fixed 16x16, centered in the 20x20 slot

	private final KubeUISidebarIcon icon;
	private final ItemStack itemStack;
	private final Font font;

	KubeUISidebarWidget(int x, int y, KubeUISidebarIcon icon, Font font) {
		super(x, y, SIZE, SIZE, Component.literal(icon.id()));
		this.icon = icon;
		// Built here (screen-open time), not when the script registered the icon (mod-construction
		// time, before item registries have their components bound - see KubeUISidebarIcon).
		this.itemStack = icon.itemIcon() != null ? new ItemStack(icon.itemIcon()) : ItemStack.EMPTY;
		this.font = font;

		if (icon.tooltip() != null && !icon.tooltip().isEmpty()) {
			setTooltip(Tooltip.create(Component.literal(icon.tooltip())));
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		icon.onClick().run();
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int bg = isHovered() ? 0xE0333333 : 0xC0000000;
		graphics.fill(getX(), getY(), getX() + SIZE, getY() + SIZE, bg);
		graphics.fill(getX(), getY(), getX() + SIZE, getY() + 1, 0xFF8B8B8B);
		graphics.fill(getX(), getY(), getX() + 1, getY() + SIZE, 0xFF8B8B8B);
		graphics.fill(getX(), getY() + SIZE - 1, getX() + SIZE, getY() + SIZE, 0xFF373737);
		graphics.fill(getX() + SIZE - 1, getY(), getX() + SIZE, getY() + SIZE, 0xFF373737);

		if (icon.itemIcon() != null) {
			graphics.item(itemStack, getX() + ITEM_INSET, getY() + ITEM_INSET);
			graphics.itemDecorations(font, itemStack, getX() + ITEM_INSET, getY() + ITEM_INSET);
		} else if (icon.textureIcon() != null) {
			int inset = 2;
			int size = SIZE - inset * 2;
			graphics.blit(RenderPipelines.GUI_TEXTURED, icon.textureIcon(), getX() + inset, getY() + inset, 0, 0, size, size, size, size);
		}
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, icon.tooltip() != null ? Component.literal(icon.tooltip()) : getMessage());
	}
}
