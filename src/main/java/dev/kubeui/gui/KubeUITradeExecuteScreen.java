package dev.kubeui.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/// The client screen for [KubeUITradeExecuteMenu] - a fully custom-drawn KubeUI screen, not a
/// reskin of vanilla's `villager.png`. This used to reuse that real texture directly (a real,
/// decompiled-verified approach that genuinely worked), but looked plain/flat and - per real user
/// feedback with a screenshot to match - has been replaced with the same modern nine-slice panel
/// family the rest of KubeUI's built-in screens now share
/// ([KubeUIPanelBackground]/[KubeUIPanelTextures]), plus hand-drawn slot frames instead of
/// vanilla's texture-baked ones (real `Slot#x`/`#y` fields, iterated generically from
/// [net.minecraft.world.inventory.AbstractContainerMenu#slots] rather than hardcoded per-slot
/// pixel math, so this stays correct even if the menu's own layout ever changes).
final class KubeUITradeExecuteScreen extends AbstractContainerScreen<KubeUITradeExecuteMenu> {
	private static final Component TRADES_LABEL = Component.translatable("merchant.trades");

	KubeUITradeExecuteScreen(KubeUITradeExecuteMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 276;
		this.imageHeight = 166;
		this.inventoryLabelX = 108;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void renderLabels(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY) {
		var graphics = new GuiGraphicsExtractor(realGraphics);
		graphics.centeredText(this.font, this.title, 49 + this.imageWidth / 2, 6, KubeUITheme.titleColor());
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, KubeUITheme.textColor(), false);
		graphics.centeredText(this.font, TRADES_LABEL, 53, 6, KubeUITheme.titleColor());
	}

	/// Draws the panel, the trade-list/payment divider, every slot's frame, and both arrows - all
	/// in this one hook rather than split across a background pass and a separate post-slots pass,
	/// since 1.21.1's `AbstractContainerScreen` has no override point between "background" and
	/// "vanilla draws the real item icons" other than this one. Frames/arrows *must* draw here
	/// (before the item-slot loop in the real `render()`), not after it, or the frame fill would
	/// paint directly over the item icons instead of behind them.
	@Override
	protected void renderBg(net.minecraft.client.gui.GuiGraphics realGraphics, float a, int mouseX, int mouseY) {
		var graphics = new GuiGraphicsExtractor(realGraphics);
		KubeUIPanelBackground.draw(graphics, KubeUIPanelTextures.TRADER_DESIGNER, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);

		// A thin divider between the trade-list column and the payment/result slots - real vanilla
		// draws a scrollbar track there instead (no scrolling is implemented here, so it would
		// always show as permanently "disabled" - a custom-drawn divider says the same thing
		// without needing a sprite that was never going to do anything).
		int dividerX = this.leftPos + 99;
		int dividerColor = (KubeUITheme.accentColor() & 0x00FFFFFF) | 0x70000000;
		graphics.fill(dividerX, this.topPos + 17, dividerX + 1, this.topPos + 17 + KubeUITradeExecuteMenu.PREVIEW_ROWS * 20, dividerColor);

		for (var slot : this.menu.slots) {
			drawSlotFrame(graphics, slot);
		}

		// One arrow per preview row that actually has a result (an empty/unused row shows nothing
		// to point at), and a second one for the real payment slots the player is actually filling
		// right now - previously only the preview-row arrows were even attempted, and both used a
		// Unicode "→" glyph that doesn't reliably render in Minecraft's default font (nothing drew
		// at all, not even a fallback glyph - a real, reported "just an empty slot" bug). Replaced
		// with the plain ASCII "->" the rest of this codebase already uses successfully elsewhere
		// (`KubeUIRecipeBridge`, the quest editor's summary rows) for exactly this reason.
		for (int row = 0; row < KubeUITradeExecuteMenu.PREVIEW_ROWS; row++) {
			if (this.menu.getSlot(KubeUITradeExecuteMenu.RESULT_PREVIEW_BASE + row).hasItem()) {
				// Cost2 preview column at x=40 (spans 40..56), result column at x=73 - centered
				// in that 17px gap. Vertically: each row's slots start at y=19+row*20 (real
				// vanilla offset) - real MerchantScreen draws its own arrow sprite at that same
				// slot-top plus 3px (decompiled and verified), which is also correct here even
				// though this is text, not a sprite: a first attempt used slot-top plus 1px,
				// sitting visibly too high (a real, reported bug) rather than centered against
				// the 16px-tall slot icons on either side of it.
				int arrowY = this.topPos + 19 + row * 20 + 3;
				graphics.centeredText(this.font, "->", this.leftPos + 64, arrowY, KubeUITheme.accentColor());
			}
		}
		// Cost2 slot spans x=162..178, result slot starts at x=220 - centered in that 42px gap.
		// Vertically: slot top is y=37, same slot-top-plus-3px rule as the preview rows above.
		graphics.centeredText(this.font, "->", this.leftPos + 199, this.topPos + 37 + 3, KubeUITheme.accentColor());
	}

	/// One real `Slot`'s worth of frame - a recessed dark square with a thin border, drawn at that
	/// slot's own real position (`slot.x`/`slot.y`, confirmed via the real decompiled `Slot` class
	/// to be exactly where its item icon draws, no hidden inset) rather than a fixed pixel region
	/// copied from a texture. `slot.x-1`/`slot.y-1` and `18x18` matches the same real 1px-margin-
	/// around-a-16x16-icon convention vanilla's own slot art uses.
	private void drawSlotFrame(GuiGraphicsExtractor graphics, Slot slot) {
		int x = this.leftPos + slot.x - 1;
		int y = this.topPos + slot.y - 1;
		graphics.fill(x, y, x + 18, y + 18, 0xE0141418);
		graphics.outline(x, y, 18, 18, 0x50FFFFFF);
	}
}
