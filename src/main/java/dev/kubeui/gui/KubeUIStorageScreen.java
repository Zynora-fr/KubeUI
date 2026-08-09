package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;

/// The real screen for [KubeUIStorageBlockEntity]'s menu - same real vanilla `generic_54.png`
/// background/layout `net.minecraft.client.gui.screens.inventory.ContainerScreen` (decompiled and
/// mirrored, not guessed - split into a top "rows" blit and a bottom "player inventory" blit,
/// vanilla's own real technique) plus two extras vanilla's shared screen can't have without a
/// custom `MenuType`: a sort button (cycles name/count/category, one `runServerAction`
/// per click) and a search field. The search *dims* non-matching slots rather than
/// hiding them - real vanilla `Slot`s stay exactly where they visually are and remain genuinely
/// clickable either way, so nothing about item pickup/placement can ever go visually-one-thing,
/// clickable-another; with this container's real cap of 27 slots (not literally "hundreds", the
/// most ambitious framing of the roadmap entry), a dim-not-hide highlight already serves the real
/// need - finding *this* stack of nails at a glance - honestly, without risking a real interaction
/// bug that isn't verifiable without an in-game session.
final class KubeUIStorageScreen extends AbstractContainerScreen<ChestMenu> {
	private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int ROWS = 3;

	private EditBox searchBox;
	private String sortKey = "name";

	KubeUIStorageScreen(ChestMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, 176, 114 + ROWS * 18);
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();

		addRenderableWidget(Button.builder(sortButtonLabel(), b -> {
			sortKey = switch (sortKey) {
				case "name" -> "count";
				case "count" -> "category";
				default -> "name";
			};
			b.setMessage(sortButtonLabel());
			var data = new CompoundTag();
			data.putString("key", sortKey);
			KubeUINetworking.sendAction(KubeUIActions.STORAGE_SORT_ACTION, data);
		}).bounds(this.leftPos, this.topPos - 22, 55, 16).build());

		searchBox = new EditBox(this.font, this.leftPos + 58, this.topPos - 22, 58, 16, Component.translatable("kubeui.storage.search"));
		searchBox.setHint(Component.translatable("kubeui.storage.search_hint"));
		addRenderableWidget(searchBox);

		addRenderableWidget(Button.builder(Component.translatable("kubeui.storage.settings"), b -> KubeUIStorageSettingsBridge.open())
			.bounds(this.leftPos + 119, this.topPos - 22, 57, 16).build());
	}

	private Component sortButtonLabel() {
		return Component.translatable("kubeui.storage.sort." + sortKey);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int rowsHeight = ROWS * 18 + 17;
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, rowsHeight, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos + rowsHeight, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);
	}

	@Override
	public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractContents(graphics, mouseX, mouseY, a);

		String query = searchBox != null ? searchBox.getValue().trim().toLowerCase() : "";
		if (query.isEmpty()) {
			return;
		}

		for (int i = 0; i < ROWS * 9; i++) {
			var slot = this.menu.slots.get(i);
			if (slot.hasItem() && !slot.getItem().getHoverName().getString().toLowerCase().contains(query)) {
				graphics.fill(this.leftPos + slot.x, this.topPos + slot.y, this.leftPos + slot.x + 16, this.topPos + slot.y + 16, 0x90000000);
			}
		}
	}
}
