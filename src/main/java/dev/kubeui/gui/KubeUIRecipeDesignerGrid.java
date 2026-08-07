package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

/// Opens [KubeUIRecipeGridMenu] (the real, interactive crafting-grid GUI) for a player and, once
/// they left-click a filled output slot, captures the grid into a [KubeUIRecipeDef] and hands it
/// to [KubeUIRecipeDesigner] to save - the player drags real items in from their own inventory
/// (a real vanilla [net.minecraft.world.inventory.ChestMenu], not a typed-in form), no ingredient
/// ids typed by hand.
final class KubeUIRecipeDesignerGrid {
	private KubeUIRecipeDesignerGrid() {
	}

	static void open(ServerPlayer player, String kind) {
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.literal("Recipe Designer - " + kind);
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new KubeUIRecipeGridMenu(containerId, inventory, kind);
			}
		});
	}

	/// Called from [KubeUIRecipeGridMenu#clicked] while the output slot's item is still present -
	/// returns `true` if a recipe was actually captured and saved (in which case the menu should
	/// skip its normal pickup handling and stays closed), `false` if the grid was empty (nothing to
	/// save, so the click should fall through to a normal pickup instead).
	static boolean tryCaptureAndSave(KubeUIRecipeGridMenu menu, String kind, ServerPlayer player) {
		var outputStack = menu.getSlot(KubeUIRecipeGridMenu.OUTPUT_INDEX).getItem();

		var ingredients = new ArrayList<String>();
		boolean anyIngredient = false;
		for (int index : KubeUIRecipeGridMenu.inputIndexes(kind)) {
			var stack = menu.getSlot(index).getItem();
			String id = stack.isEmpty() ? "" : itemId(stack);
			if (!id.isEmpty()) {
				anyIngredient = true;
			}
			ingredients.add(id);
		}

		if (!anyIngredient) {
			player.sendSystemMessage(Component.literal("Put at least one ingredient in the grid before taking the result."));
			return false;
		}

		if (!kind.equals("shaped")) {
			// shapeless/cooking/stonecutting/smithing don't care about blank cells - only the
			// filled slots, in order, matter for KubeUIRecipeDesigner#toJs.
			ingredients.removeIf(String::isEmpty);
		}

		String name = kind + "_" + itemId(outputStack).replace(':', '_') + "_" + uniqueSuffix();
		var def = new KubeUIRecipeDef(name, kind, ingredients, itemId(outputStack), outputStack.getCount(), 200, 0f);

		if (KubeUIRecipeDesigner.save(def, player)) {
			player.closeContainer();
			return true;
		}
		return false;
	}

	private static int nameCounter = 0;

	private static String uniqueSuffix() {
		return Integer.toString(++nameCounter);
	}

	private static String itemId(ItemStack stack) {
		var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return key != null ? key.toString() : "";
	}
}
