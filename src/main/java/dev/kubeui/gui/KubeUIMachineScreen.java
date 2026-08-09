package dev.kubeui.gui;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/// The screen for [KubeUIMachineMenu] - a fully custom-drawn panel, not a reused vanilla sprite
/// (the earlier version blitted `furnace.png`, which doesn't actually match this block's own
/// upgrade/input/output/fuel shape and read as visually mismatched - real, reported feedback, not
/// guessed). Layout mirrors the same input-above-fuel-with-a-flame-between-them, output-off-to-the-
/// side-via-an-arrow spatial convention every furnace-like screen already uses (recognizable on
/// sight) even though nothing here is drawn from vanilla's own texture. Every color comes from
/// [KubeUITheme] where it should track a player's theme (title/status/accent); the panel's own
/// dark chrome (gradients, bevels, card chamfers) is deliberately fixed - the same "beau GUI... des
/// textures en Code directement en Java" redesign pass ["comme Paladium"] asked for a real layered
/// look (gradients + bevelled insets + chamfered cards) built entirely from
/// [GuiGraphicsExtractor#fill]/[GuiGraphicsExtractor#fillGradient]/[GuiGraphicsExtractor#outline],
/// not a hand-drawn texture asset.
///
/// While [KubeUIMachineBlockEntity#STATUS_NO_KIND] (a freshly-placed block with no kind chosen
/// yet - "on a accès qu'à la Machine, pas au truc Crusher/Smelter"), this draws a kind picker
/// instead of the normal slot layout - real vanilla item icons for each registered kind's output,
/// synced through [KubeUIMachineMenu]'s own fake preview slots, click one to become that kind.
/// [KubeUIMachineBlockEntity#STATUS_INVALID_KIND] (a kind *was* chosen but no longer resolves - a
/// real, reported bug where this used to share the picker's own status code, so an already-kinded
/// "Crusher" machine kept re-showing the picker instead of a clear error) gets its own plain error
/// message instead - re-showing the picker there would silently let a script bug look like "please
/// choose again" rather than "this machine's kind is broken".
final class KubeUIMachineScreen extends AbstractContainerScreen<KubeUIMachineMenu> {
	private static final int PANEL_TOP = 0xF02C2F35;
	private static final int PANEL_BOTTOM = 0xF015161A;
	private static final int PANEL_BORDER = 0xFF08080A;
	private static final int PANEL_HIGHLIGHT = 0x28FFFFFF;
	private static final int HEADER_DIVIDER = 0xFF3D4048;

	private static final int SLOT_BG_TOP = 0xFF24262A;
	private static final int SLOT_BG_BOTTOM = 0xFF18191C;
	private static final int SLOT_EDGE_DARK = 0xFF0A0A0C;
	private static final int SLOT_EDGE_LIGHT = 0xFF44474E;

	private static final int CARD_BG_TOP = 0xFF282A2F;
	private static final int CARD_BG_BOTTOM = 0xFF1B1C20;
	private static final int CARD_BORDER = 0xFF3D4048;
	private static final int CARD_CHAMFER = PANEL_BOTTOM;

	private static final int BAR_BG = 0xFF101113;
	private static final int ENERGY_TOP = 0xFFFFC060;
	private static final int ENERGY_BOTTOM = 0xFFD4872A;
	private static final int LIT_COLOR = 0xFFFF9020;

	private static final int SLOT_UPGRADE_X = 8, SLOT_UPGRADE_Y = 38;
	private static final int SLOT_INPUT_X = 56, SLOT_INPUT_Y = 38;
	private static final int SLOT_FUEL_X = 56, SLOT_FUEL_Y = 74;
	private static final int SLOT_OUTPUT_X = 126, SLOT_OUTPUT_Y = 56;
	private static final int ENERGY_X = 78, ENERGY_Y = 74, ENERGY_W = 10, ENERGY_H = 18;
	private static final int PROGRESS_X = 92, PROGRESS_Y = 61, PROGRESS_W = 32, PROGRESS_H = 8;
	private static final int INV_X = 8, INV_Y = 106;

	KubeUIMachineScreen(KubeUIMachineMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 192;
		this.titleLabelX = 8;
		this.titleLabelY = 6;
		this.inventoryLabelX = INV_X;
		this.inventoryLabelY = INV_Y - 10;
	}

	/// A real, reported gap: nothing on screen said which slot was which ("comment on alimente la
	/// machine" - real user confusion, not guessed) - full slot names plus a live "N crafted" count
	/// (see [KubeUIMachineMenu#crafted]) make it obvious the machine is actually doing something,
	/// not just sitting there. `extractLabels` draws in *local* coordinates (no `leftPos`/`topPos`
	/// offset - decompiled and confirmed from [KubeUITradeExecuteScreen]'s own real title/inventory-
	/// label calls), unlike [#extractBackground]/slot positions themselves, which are absolute.
	@Override
	protected void renderLabels(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY) {
		var graphics = new GuiGraphicsExtractor(realGraphics);
		graphics.text(this.font, this.title, this.titleLabelX, this.titleLabelY, KubeUITheme.titleColor(), false);

		String status = statusText();
		graphics.text(this.font, status, imageWidth - 8 - font.width(status), this.titleLabelY + 10, statusColor(), false);

		int machineStatus = menu.status();
		if (machineStatus == KubeUIMachineBlockEntity.STATUS_NO_KIND) {
			graphics.text(this.font, "Choose what this machine does:", this.titleLabelX, this.titleLabelY + 10, dim(), false);
			for (int i = 0; i < KubeUIMachineMenu.PICKER_ROWS; i++) {
				var stack = menu.slots.get(KubeUIMachineMenu.MACHINE_SLOT_COUNT + i).getItem();
				if (!stack.isEmpty()) {
					int rowTop = KubeUIMachineMenu.PICKER_CARD_Y + i * KubeUIMachineMenu.PICKER_ROW_STRIDE;
					int textY = rowTop + KubeUIMachineMenu.PICKER_CARD_HEIGHT / 2 - 4;
					graphics.text(this.font, stack.getHoverName(), KubeUIMachineMenu.PICKER_ICON_X + 21, textY, KubeUITheme.textColor(), false);
					String chevron = ">";
					graphics.text(this.font, chevron, KubeUIMachineMenu.PICKER_CARD_X + KubeUIMachineMenu.PICKER_CARD_WIDTH - 10, textY, dim(), false);
				}
			}
		} else if (machineStatus == KubeUIMachineBlockEntity.STATUS_INVALID_KIND) {
			graphics.centeredText(this.font, "This machine's kind ('" + menu.machine().kind() + "') is no longer valid.", imageWidth / 2, 60, 0xFFFF8080);
			graphics.centeredText(this.font, "Ask whoever built this pack to check their scripts.", imageWidth / 2, 72, dim());
		} else {
			graphics.text(this.font, menu.crafted() + " crafted", this.titleLabelX, this.titleLabelY + 10, dim(), false);

			graphics.centeredText(this.font, "Upgrade", SLOT_UPGRADE_X + 9, SLOT_UPGRADE_Y - 10, dim());
			graphics.centeredText(this.font, "Input", SLOT_INPUT_X + 9, SLOT_INPUT_Y - 10, dim());
			graphics.centeredText(this.font, "Fuel", SLOT_FUEL_X + 9, SLOT_FUEL_Y + 20, dim());
			graphics.centeredText(this.font, "Output", SLOT_OUTPUT_X + 9, SLOT_OUTPUT_Y + 20, dim());
		}

		graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, dim(), false);
	}

	/// Spells out *why* the machine isn't running right now ("je met cobblestone dans Input il se
	/// passe rien" - a player had no way to tell "waiting on fuel" from "waiting on a valid recipe"
	/// from "actually fine, just needs a moment" apart from a throttled, easy-to-miss chat alert).
	/// Kept short (right-aligned next to the "N crafted" subtitle, no room for a full sentence) - See
	/// [KubeUIMachineBlockEntity#STATUS_NO_KIND] and siblings for what each code means.
	private String statusText() {
		return switch (menu.status()) {
			case KubeUIMachineBlockEntity.STATUS_NO_KIND -> "No kind";
			case KubeUIMachineBlockEntity.STATUS_INVALID_KIND -> "Invalid kind";
			case KubeUIMachineBlockEntity.STATUS_REDSTONE_BLOCKED -> "Blocked";
			case KubeUIMachineBlockEntity.STATUS_WAITING_FOR_INPUT -> "No input";
			case KubeUIMachineBlockEntity.STATUS_NEEDS_FUEL -> "Needs fuel";
			case KubeUIMachineBlockEntity.STATUS_OUTPUT_FULL -> "Output full";
			case KubeUIMachineBlockEntity.STATUS_RUNNING -> "Running...";
			default -> "";
		};
	}

	private int statusColor() {
		return menu.status() == KubeUIMachineBlockEntity.STATUS_RUNNING ? (KubeUITheme.accentColor() | 0xFF000000) : 0xFFFF8080;
	}

	@Override
	protected void renderBg(net.minecraft.client.gui.GuiGraphics realGraphics, float partialTick, int mouseX, int mouseY) {
		var graphics = new GuiGraphicsExtractor(realGraphics);
		int x = this.leftPos;
		int y = this.topPos;

		graphics.fillGradient(x, y, x + imageWidth, y + imageHeight, PANEL_TOP, PANEL_BOTTOM);
		graphics.outline(x, y, imageWidth, imageHeight, PANEL_BORDER);
		graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + 2, PANEL_HIGHLIGHT);
		graphics.fill(x + 1, y + 21, x + imageWidth - 1, y + 22, HEADER_DIVIDER);

		int machineStatus = menu.status();
		if (machineStatus == KubeUIMachineBlockEntity.STATUS_NO_KIND) {
			for (int i = 0; i < KubeUIMachineMenu.PICKER_ROWS; i++) {
				var stack = menu.slots.get(KubeUIMachineMenu.MACHINE_SLOT_COUNT + i).getItem();
				if (stack.isEmpty()) {
					continue;
				}
				int cardX = x + KubeUIMachineMenu.PICKER_CARD_X;
				int cardY = y + KubeUIMachineMenu.PICKER_CARD_Y + i * KubeUIMachineMenu.PICKER_ROW_STRIDE;
				int cardW = KubeUIMachineMenu.PICKER_CARD_WIDTH;
				int cardH = KubeUIMachineMenu.PICKER_CARD_HEIGHT;
				boolean hovered = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= cardY && mouseY < cardY + cardH;
				drawCard(graphics, cardX, cardY, cardW, cardH, hovered);

				int iconX = x + KubeUIMachineMenu.PICKER_ICON_X - 3;
				int iconY = y + KubeUIMachineMenu.PICKER_ICON_Y + i * KubeUIMachineMenu.PICKER_ROW_STRIDE - 1;
				drawInsetSlot(graphics, iconX, iconY);
			}
		} else if (machineStatus != KubeUIMachineBlockEntity.STATUS_INVALID_KIND) {
			drawInsetSlot(graphics, x + SLOT_UPGRADE_X, y + SLOT_UPGRADE_Y);
			drawInsetSlot(graphics, x + SLOT_INPUT_X, y + SLOT_INPUT_Y);
			drawInsetSlot(graphics, x + SLOT_OUTPUT_X, y + SLOT_OUTPUT_Y);
			drawInsetSlot(graphics, x + SLOT_FUEL_X, y + SLOT_FUEL_Y);
			if (menu.lit()) {
				graphics.outline(x + SLOT_FUEL_X, y + SLOT_FUEL_Y, 18, 18, LIT_COLOR);
			}

			// The flame sits in the exact gap between the input slot's bottom and the fuel slot's top -
			// "rajouter un truc de feu pour dire que ça consomme le Fuel" (real ask: make it visibly
			// obvious fuel is actually being burned, not just an energy number climbing somewhere).
			// Built from plain rects (no sprite dependency) - a tapering silhouette narrowing toward the
			// tip, exactly the shape every furnace-style flame icon uses.
			drawFlame(graphics, x + SLOT_INPUT_X + 9, y + SLOT_FUEL_Y, menu.lit());

			int barX = x + PROGRESS_X, barY = y + PROGRESS_Y;
			graphics.fill(barX, barY, barX + PROGRESS_W, barY + PROGRESS_H, BAR_BG);
			graphics.outline(barX - 1, barY - 1, PROGRESS_W + 2, PROGRESS_H + 2, PANEL_BORDER);
			int filled = menu.maxProgress() > 0 ? PROGRESS_W * menu.progress() / menu.maxProgress() : 0;
			if (filled > 0) {
				graphics.fillGradient(barX, barY, barX + filled, barY + PROGRESS_H, KubeUITheme.accentColor() | 0xFF000000, darken(KubeUITheme.accentColor()));
			}
			// A small arrowhead at the right end reads as "input flows this way into output" at a
			// glance, the same visual grammar a furnace's own progress arrow uses.
			int arrowX = barX + PROGRESS_W;
			int arrowY = barY + PROGRESS_H / 2;
			graphics.fill(arrowX, arrowY - 3, arrowX + 2, arrowY - 1, PANEL_BORDER);
			graphics.fill(arrowX, arrowY + 1, arrowX + 2, arrowY + 3, PANEL_BORDER);
			graphics.fill(arrowX + 2, arrowY - 1, arrowX + 4, arrowY + 1, PANEL_BORDER);

			int energyX = x + ENERGY_X, energyY = y + ENERGY_Y;
			graphics.fill(energyX, energyY, energyX + ENERGY_W, energyY + ENERGY_H, BAR_BG);
			graphics.outline(energyX - 1, energyY - 1, ENERGY_W + 2, ENERGY_H + 2, PANEL_BORDER);
			int energyFilled = menu.maxEnergy() > 0 ? ENERGY_H * menu.energy() / menu.maxEnergy() : 0;
			if (energyFilled > 0) {
				graphics.fillGradient(energyX, energyY + ENERGY_H - energyFilled, energyX + ENERGY_W, energyY + ENERGY_H, ENERGY_TOP, ENERGY_BOTTOM);
			}
		}

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				drawInsetSlot(graphics, x + INV_X + col * 18, y + INV_Y + row * 18);
			}
		}
		for (int col = 0; col < 9; col++) {
			drawInsetSlot(graphics, x + INV_X + col * 18, y + INV_Y + 58);
		}
	}

	/// A recessed-look slot (dark top/left edge, lit bottom/right edge, gradient-filled interior) -
	/// the same real bevel convention vanilla's own item slots use to read as "a socket the item
	/// drops into", replacing the earlier flat single-color box.
	private void drawInsetSlot(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.fillGradient(x, y, x + 18, y + 18, SLOT_BG_TOP, SLOT_BG_BOTTOM);
		graphics.fill(x, y, x + 18, y + 1, SLOT_EDGE_DARK);
		graphics.fill(x, y, x + 1, y + 18, SLOT_EDGE_DARK);
		graphics.fill(x, y + 17, x + 18, y + 18, SLOT_EDGE_LIGHT);
		graphics.fill(x + 17, y, x + 18, y + 18, SLOT_EDGE_LIGHT);
	}

	/// A full-width picker row, chamfered corners faked by painting the panel's own background color
	/// back over each 1x1 corner after the card fills its rect - a cheap, real technique for "rounded"
	/// corners that only works because the card always sits directly over this exact panel (not a
	/// generally-reusable rounded-rect primitive, [GuiGraphicsExtractor] doesn't have one).
	private void drawCard(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean hovered) {
		graphics.fillGradient(x, y, x + w, y + h, CARD_BG_TOP, CARD_BG_BOTTOM);
		int border = hovered ? (KubeUITheme.accentColor() | 0xFF000000) : CARD_BORDER;
		graphics.outline(x, y, w, h, border);
		if (hovered) {
			graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, (KubeUITheme.accentColor() & 0x00FFFFFF) | 0x18000000);
		}
		graphics.fill(x, y, x + 1, y + 1, CARD_CHAMFER);
		graphics.fill(x + w - 1, y, x + w, y + 1, CARD_CHAMFER);
		graphics.fill(x, y + h - 1, x + 1, y + h, CARD_CHAMFER);
		graphics.fill(x + w - 1, y + h - 1, x + w, y + h, CARD_CHAMFER);
	}

	/// A tapering, teardrop-ish flame silhouette drawn from four stacked rects (widest/darkest at
	/// the base, narrowest/brightest at the tip) - deliberately not a font glyph (no vanilla-font
	/// guarantee any fire/flame Unicode character actually has a glyph) or a reused sprite (this
	/// screen no longer depends on `furnace.png` at all), just primitives already proven safe
	/// elsewhere on this same screen. `centerX`/`baseY` is the flame's bottom-center point.
	private void drawFlame(GuiGraphicsExtractor graphics, int centerX, int baseY, boolean lit) {
		if (!lit) {
			graphics.fill(centerX - 2, baseY - 4, centerX + 2, baseY - 1, 0xFF3A3A3A);
			return;
		}
		graphics.fill(centerX - 4, baseY - 3, centerX + 4, baseY, 0xFFB03000);
		graphics.fill(centerX - 3, baseY - 6, centerX + 3, baseY - 3, 0xFFFF7A1A);
		graphics.fill(centerX - 2, baseY - 9, centerX + 2, baseY - 6, 0xFFFFD23D);
		graphics.fill(centerX - 1, baseY - 11, centerX + 1, baseY - 9, 0xFFFFF3B0);
	}

	private static int darken(int argb) {
		int a = argb & 0xFF000000;
		int r = (int) (((argb >> 16) & 0xFF) * 0.55);
		int g = (int) (((argb >> 8) & 0xFF) * 0.55);
		int b = (int) ((argb & 0xFF) * 0.55);
		return a | (r << 16) | (g << 8) | b;
	}

	private static int dim() {
		return (KubeUITheme.textColor() & 0x00FFFFFF) | 0xA0000000;
	}
}
