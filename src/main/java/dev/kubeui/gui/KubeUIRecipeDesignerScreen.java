package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/// The in-game Recipe Designer screen (`/kubeui recipe-designer`) - see [KubeUIRecipeDesigner] for
/// what actually happens server-side when "Save" is pressed. Same static-state-plus-`refresh()`
/// pattern already established by [KubeUIFileEditor] - simplest reliable way to keep a form's
/// in-progress values across the rebuilds every list update triggers.
public final class KubeUIRecipeDesignerScreen {
	private static List<CompoundTag> lastKnownRecipes = List.of();

	private KubeUIRecipeDesignerScreen() {
	}

	public static void open() {
		var data = new CompoundTag();
		data.putString("value", "");
		KubeUINetworking.sendAction(KubeUIActions.RECIPE_DESIGNER_LIST_ACTION, data);
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	private static void refresh() {
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	static void receiveList(ListTag recipes) {
		var list = new ArrayList<CompoundTag>();
		for (var entry : recipes) {
			if (entry instanceof CompoundTag tag) {
				list.add(tag);
			}
		}
		lastKnownRecipes = list;
		refresh();
	}

	private static KubeUIScreenBuilder buildScreen() {
		var window = Minecraft.getInstance().getWindow();
		int totalWidth = clamp((int) (window.getGuiScaledWidth() * 0.8), 340, 620);
		int listHeight = clamp((int) (window.getGuiScaledHeight() * 0.3), 80, 200);

		var b = KubeUIScreenBuilder.builder("Recipe Designer")
			.draggable()
			.elementSize(totalWidth, 20)
			.windowBackground(KubeUIPanelTextures.RECIPE_DESIGNER);

		b.row(row -> {
			row.label("existingLabel", lastKnownRecipes.isEmpty() ? "No custom recipes saved yet." : lastKnownRecipes.size() + " saved custom recipe(s):").width(totalWidth - 130);
			row.button("Reload Recipes", ctx -> KubeUINetworking.sendAction(KubeUIActions.RECIPE_DESIGNER_RELOAD_ACTION, new CompoundTag())).width(130);
		});
		b.label("reloadHint", "Saving/deleting doesn't reload automatically (that's a real lag spike) - click Reload Recipes above once you're done.");
		if (!lastKnownRecipes.isEmpty()) {
			b.scrollPanel(listHeight, panel -> {
				for (var tag : lastKnownRecipes) {
					String name = KubeUINbtCompat.getStringOr(tag, "name", "?");
					panel.row(row -> {
						row.label("l_" + name, name + " (" + KubeUINbtCompat.getStringOr(tag, "kind", "?") + ")").width(totalWidth - 80);
						row.button("Delete", ctx -> {
							var data = new CompoundTag();
							data.putString("name", name);
							KubeUINetworking.sendAction(KubeUIActions.RECIPE_DESIGNER_DELETE_ACTION, data);
						}).width(60);
					});
				}
			});
		}
		b.divider();

		b.label("newLabel", "New recipe - pick a kind, then arrange it in a real crafting-grid GUI:");
		int perRow = 2;
		var kinds = KubeUIRecipeDesigner.KINDS;
		for (int i = 0; i < kinds.size(); i += perRow) {
			final int start = i;
			b.row(row -> {
				for (int j = start; j < Math.min(start + perRow, kinds.size()); j++) {
					String kind = kinds.get(j);
					row.button(kindLabel(kind), ctx -> openGrid(kind)).width((totalWidth - 10) / perRow);
				}
			});
		}

		b.divider();
		b.button("Close", ctx -> ctx.close());
		return b;
	}

	private static String kindLabel(String kind) {
		String[] parts = kind.split("_");
		var sb = new StringBuilder();
		for (String part : parts) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	private static void openGrid(String kind) {
		var data = new CompoundTag();
		data.putString("kind", kind);
		KubeUINetworking.sendAction(KubeUIActions.RECIPE_DESIGNER_OPEN_GRID_ACTION, data);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
