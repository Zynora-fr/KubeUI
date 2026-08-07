package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/// A row of stars (`.rating(id, max, initial, onChange)`), clickable unless
/// `onChange` is null (read-only display). Drawn as text glyphs (★/☆) rather than custom
/// geometry - Minecraft's font renderer already covers these via its Unicode fallback pages, the
/// same mechanism that renders any other non-ASCII character.
class KubeUIRating extends AbstractWidget implements KubeUINarratable {
	private static final char FILLED = '★';
	private static final char EMPTY = '☆';
	private static final int STAR_SIZE = 12;

	private final int max;
	private final Font font;
	private final BiConsumer<KubeUIContext, Integer> onChange;
	private final KubeUIContext context;
	private int value;
	private String narration;

	KubeUIRating(int x, int y, int max, int initial, Font font, KubeUIContext context, BiConsumer<KubeUIContext, Integer> onChange) {
		super(x, y, STAR_SIZE * Math.max(1, max), STAR_SIZE, Component.literal("Rating"));
		this.max = Math.max(1, max);
		this.value = KubeUILayoutMath.clampInt(initial, 0, this.max);
		this.font = font;
		this.context = context;
		this.onChange = onChange;
		this.active = onChange != null;
	}

	int value() {
		return value;
	}

	void setValue(int newValue) {
		this.value = KubeUILayoutMath.clampInt(newValue, 0, max);
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (!active) {
			return;
		}
		int clickedStar = (int) ((event.x() - getX()) / STAR_SIZE) + 1;
		value = KubeUILayoutMath.clampInt(clickedStar, 1, max);
		onChange.accept(context, value);
	}

	/// Left/Right adjusts by one star - added alongside the new focus outline ([KubeUIFocusOutline])
	/// since a widget a keyboard user can now clearly see is focused should also be operable by
	/// keyboard, not just visually highlighted.
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!active || !(event.isLeft() || event.isRight())) {
			return false;
		}
		value = KubeUILayoutMath.clampInt(value + (event.isRight() ? 1 : -1), 1, max);
		onChange.accept(context, value);
		return true;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int hoverStar = active && isHovered() ? (int) ((mouseX - getX()) / STAR_SIZE) + 1 : -1;

		for (int i = 0; i < max; i++) {
			boolean filled = i < (hoverStar > 0 ? hoverStar : value);
			int color = filled ? 0xFFFFD700 : 0xFF6B7679;
			graphics.text(font, String.valueOf(filled ? FILLED : EMPTY), getX() + i * STAR_SIZE, getY() + 1, color, false);
		}

		KubeUIFocusOutline.draw(graphics, this);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : value + "/" + max));
	}
}
