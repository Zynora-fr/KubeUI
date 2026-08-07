package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/// Renders a JEI-style "any of these" ingredient slot - like [KubeUIItemWidget], but cycles
/// through every stack in `stacks` once a second (real time, not tied to `rebuild()`, so it
/// animates without needing continuous rebuilds) instead of showing a single fixed item. An empty
/// `stacks` list renders as a plain empty slot rather than failing.
class KubeUIRecipeSlotWidget extends AbstractWidget implements KubeUINarratable {
	private final DoubleClickTracker doubleClickTracker = new DoubleClickTracker();
	private static final long CYCLE_MS = 1000;

	private final List<ItemStack> stacks;
	private final Font font;
	private final Consumer<MouseButtonEvent> onClick;
	private String narration;

	KubeUIRecipeSlotWidget(int x, int y, List<ItemStack> stacks, Font font, Consumer<MouseButtonEvent> onClick) {
		super(x, y, 16, 16, currentLabel(stacks, 0));
		this.active = onClick != null;
		this.stacks = stacks;
		this.font = font;
		this.onClick = onClick;
	}

	private static Component currentLabel(List<ItemStack> stacks, long nowMs) {
		if (stacks.isEmpty()) {
			return Component.empty();
		}
		int index = (int) ((nowMs / CYCLE_MS) % stacks.size());
		return stacks.get(index).getHoverName();
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	public void onClick(double mouseX, double mouseY, int button) {
		onClick(new MouseButtonEvent(mouseX, mouseY, button), doubleClickTracker.registerClick(mouseX, mouseY));
	}

	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (onClick != null) {
			onClick.accept(event);
		}
	}

	@Override
	protected void renderWidget(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		extractWidgetRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		if (active && isHovered()) {
			graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0x80FFFFFF);
		}

		if (stacks.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();
		var stack = stacks.get((int) ((now / CYCLE_MS) % stacks.size()));
		setMessage(currentLabel(stacks, now));
		graphics.item(stack, getX(), getY());
		graphics.itemDecorations(font, stack, getX(), getY());
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, narration != null ? Component.literal(narration) : getMessage());
	}
}
