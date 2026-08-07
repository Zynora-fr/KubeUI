package dev.kubeui.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.BiConsumer;

/// Lets a script offer a rebindable key without going through vanilla Controls
/// (`.keybindCapture(id, initial, onChange)`). Click to start listening, next key press is
/// captured and becomes the new binding; `onChange` receives the raw key code (an `int`, same
/// type `KeyEvent#key()` uses) - persisting/comparing it is the script's job, same as `.number()`.
class KubeUIKeybindCapture extends AbstractWidget implements KubeUINarratable {
	private final Font font;
	private final BiConsumer<KubeUIContext, Integer> onChange;
	private final KubeUIContext context;
	private int keyCode;
	private int scancode;
	private boolean listening;
	private String narration;
	private final Integer styleColor;

	KubeUIKeybindCapture(int x, int y, int width, int height, int initialKeyCode, Font font, KubeUIContext context, BiConsumer<KubeUIContext, Integer> onChange, Integer styleColor) {
		super(x, y, width, height, Component.literal("Keybind"));
		this.keyCode = initialKeyCode;
		this.scancode = -1;
		this.font = font;
		this.context = context;
		this.onChange = onChange;
		this.styleColor = styleColor;
	}

	int keyCode() {
		return keyCode;
	}

	private Component displayLabel() {
		if (listening) {
			return Component.literal("> Press a key <");
		}

		InputConstants.Key key = keyCode == -1
			? InputConstants.Type.SCANCODE.getOrCreate(scancode)
			: InputConstants.Type.KEYSYM.getOrCreate(keyCode);
		return key.getDisplayName();
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		listening = true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!listening) {
			// Enter/Space starts listening, same as a click would - without this, a keyboard-only
			// player could Tab to this field but never actually rebind it (this widget extends
			// AbstractWidget directly, which - unlike AbstractButton/Checkbox - doesn't implement
			// any keyPressed of its own to fall back on).
			if (event.isSelection()) {
				listening = true;
				return true;
			}
			return false;
		}

		listening = false;
		keyCode = event.key();
		scancode = event.scancode();
		onChange.accept(context, keyCode);
		return true;
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int bg = listening ? 0xFF45D6C9 : isHovered() ? 0x40FFFFFF : 0x20FFFFFF;
		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
		graphics.outline(getX(), getY(), getWidth(), getHeight(), 0xFF6B7679);

		Component label = displayLabel();
		int textColor = listening ? 0xFF0A1413 : (styleColor != null ? styleColor : KubeUITheme.textColor());
		int lx = getX() + getWidth() / 2;
		int ly = getY() + (getHeight() - font.lineHeight) / 2 + 1;
		KubeUIFontScale.draw(graphics, lx, ly, () -> graphics.centeredText(font, label, lx, ly, textColor));

		KubeUIFocusOutline.draw(graphics, this);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, narration != null ? Component.literal(narration) : displayLabel());
	}
}
