package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/// Renders an item icon (with its count/durability overlay), exactly like an inventory slot.
/// Clickable (and hover-highlighted) only if `onClick` is non-null.
class KubeUIItemWidget extends AbstractWidget implements KubeUINarratable {
	private final ItemStack stack;
	private final Font font;
	private final Consumer<MouseButtonEvent> onClick;
	private String narration;

	KubeUIItemWidget(int x, int y, ItemStack stack, Font font, Consumer<MouseButtonEvent> onClick) {
		super(x, y, 16, 16, stack.getHoverName());
		this.active = onClick != null;
		this.stack = stack;
		this.font = font;
		this.onClick = onClick;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (onClick != null) {
			onClick.accept(event);
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (active && isHovered()) {
			graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x80FFFFFF);
		}

		graphics.item(stack, getX(), getY());
		graphics.itemDecorations(font, stack, getX(), getY());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, narration != null ? Component.literal(narration) : getMessage());
	}
}
