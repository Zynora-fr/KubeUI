package dev.kubeui.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/// The client screen for [KubeUIRecipeGridMenu] - reuses whichever real vanilla container texture
/// matches the kind it was opened for (`textures/gui/container/crafting_table.png`/`furnace.png`/
/// `blast_furnace.png`/`smoker.png`/`stonecutter.png`/`smithing.png`, all real assets always
/// present in the base game, not something this mod ships - see [KubeUIRecipeGridScreenHook] for
/// which texture each registered `MenuType` maps to), at the same 176x166 size vanilla's own
/// screens for these use. Only ever referenced from [KubeUIRecipeGridScreenHook], which is
/// `Dist.CLIENT`-gated - that's what actually keeps this off a dedicated server, not an `@OnlyIn`
/// annotation (this NeoForge version no longer strips members at runtime for it - see
/// [KubeUIConfigScreenHook] for the pattern this codebase actually relies on instead).
final class KubeUIRecipeGridScreen extends AbstractContainerScreen<KubeUIRecipeGridMenu> {
	private final Identifier texture;

	KubeUIRecipeGridScreen(KubeUIRecipeGridMenu menu, Inventory inventory, Component title, Identifier texture) {
		super(menu, inventory, title, 176, 166);
		this.texture = texture;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
	}
}
