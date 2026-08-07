package dev.kubeui.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/// A live-rendered entity preview (`.entityPreview(entityType, width,
/// height)`), via the exact same picture-in-picture mechanism/helper vanilla uses for the player
/// model in the survival inventory screen (`InventoryScreen.renderEntityInInventoryFollowsAngle`)
/// - not reimplemented from scratch. The entity is constructed with `EntityType#create` and never
/// added to a level, so it only ever exists as something to render, never something that ticks or
/// is visible to anyone else.
///
/// There's no `.blockPreview(...)` widget class - real picture-in-picture GUI rendering (the
/// mechanism this class relies on) only has a render-state type for entities in this version, not
/// blocks, so `KubeUIScreenBuilder#blockPreview` reuses the existing item-icon widget on the
/// block's `BlockItem` instead of pretending to do true 3D block rendering.
class KubeUIEntityPreview extends AbstractWidget implements KubeUINarratable {
	private final LivingEntity entity;
	private String narration;

	KubeUIEntityPreview(int x, int y, int width, int height, LivingEntity entity) {
		super(x, y, width, height, entity.getName());
		this.entity = entity;
		this.active = false;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	protected void renderWidget(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		extractWidgetRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int size = Math.min(getWidth(), getHeight()) * 4 / 5;
		// xAngle/yAngle are radians (same unit `Math.atan(...)` produces for the vanilla
		// mouse-following variant of this call) - a fixed mild turn gives a static three-quarter
		// view instead of a flat front-on one.
		InventoryScreen.renderEntityInInventoryFollowsAngle(
			graphics.real(), getX(), getY(), getX() + getWidth(), getY() + getHeight(), size, 0.0F, 0.4F, 0.1F, entity
		);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, narration != null ? Component.literal(narration) : getMessage());
	}
}
