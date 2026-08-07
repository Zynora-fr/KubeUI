package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/// The client screen for [KubeUITraderGridMenu] - a fully custom-drawn KubeUI screen (see
/// [KubeUITradeExecuteScreen]'s class doc for the fuller reasoning: this used to reuse real
/// vanilla `villager.png`, which worked but looked plain, and was replaced with the same modern
/// nine-slice panel + hand-drawn slot frames every other built-in KubeUI screen now shares).
final class KubeUITraderGridScreen extends AbstractContainerScreen<KubeUITraderGridMenu> {
	KubeUITraderGridScreen(KubeUITraderGridMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 276, 166);
		this.inventoryLabelX = 108;
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.centeredText(this.font, this.title, 49 + this.imageWidth / 2, 6, KubeUITheme.titleColor());
		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, KubeUITheme.textColor(), false);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		KubeUIPanelBackground.draw(graphics, KubeUIPanelTextures.TRADER_DESIGNER, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		for (var slot : this.menu.slots) {
			drawSlotFrame(graphics, slot);
		}

		// The single cost1+cost2 -> result arrow, at real vanilla MerchantMenu slot coordinates
		// (136,37 / 162,37 / 220,37) - centered in the gap between the 2nd cost slot (spans
		// x=162..178) and the result slot (starts at x=220). Plain ASCII "->", not a Unicode "→"
		// glyph - the latter doesn't reliably render in Minecraft's default font (a real, reported
		// bug: it silently drew nothing at all, not even a fallback glyph).
		// Vertically: slot top is y=37, centered via the same slot-top-plus-3px rule
		// [KubeUITradeExecuteScreen] uses (matches real MerchantScreen's own arrow-sprite offset).
		graphics.centeredText(this.font, "->", this.leftPos + 199, this.topPos + 37 + 3, KubeUITheme.accentColor());

		super.extractContents(graphics, mouseX, mouseY, a);
	}

	private void drawSlotFrame(GuiGraphicsExtractor graphics, Slot slot) {
		int x = this.leftPos + slot.x - 1;
		int y = this.topPos + slot.y - 1;
		graphics.fill(x, y, x + 18, y + 18, 0xE0141418);
		graphics.outline(x, y, 18, 18, 0x50FFFFFF);
	}
}
