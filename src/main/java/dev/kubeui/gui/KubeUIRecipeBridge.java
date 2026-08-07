package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/// Client-side half of `KubeUI.recipeScreen(recipeTypeId)`/`.recipesFor(itemId, onResult)` - sends
/// the query ([KubeUIRecipeQuery] does the actual lookup, server-side) and reacts to the reply,
/// intercepted directly in [KubeUINetworking#handleRemote] the same way permission-check/sidebar-
/// visibility replies already are. `.recipesFor(...)`'s result is a plain `List<CompoundTag>`
/// (`.getStringOr(...)`/`.getListOrEmpty(...)`, the same accessors every other `data` parameter in
/// this codebase already uses from scripts) rather than converted to a `Map`/JS object - `List<T>`
/// callback parameters are already proven to behave like a real JS array from a script
/// (`.checkboxGroup(...)`'s `onChange(screen, values)` already does exactly this), so this reuses
/// that same proven shape instead of a second, unverified one.
///
/// [#receiveTypeResult]'s screen sizes itself from the *actual* window (same technique
/// [KubeUIFileEditor] already uses) - a recipe list can be long, and a fixed pixel height either
/// wastes space on a big window or overflows a small one.
final class KubeUIRecipeBridge {
	/// `.recipesFor(...)` is fire-and-forget from a script's perspective - only the most recent
	/// call's callback is kept (a second call before the first replies replaces it, it isn't
	/// queued), the simplest honest behavior without inventing a request-id-per-call system for a
	/// query that normally resolves within one network round trip anyway.
	private static BiConsumer<KubeUIContext, List<CompoundTag>> pendingItemQuery;

	private KubeUIRecipeBridge() {
	}

	static void requestRecipeScreen(String recipeTypeId) {
		var data = new CompoundTag();
		data.putString("value", recipeTypeId);
		KubeUINetworking.sendAction(KubeUIActions.RECIPE_QUERY_TYPE_ACTION, data);
	}

	static void requestRecipesFor(String itemId, BiConsumer<KubeUIContext, List<CompoundTag>> onResult) {
		pendingItemQuery = onResult;
		var data = new CompoundTag();
		data.putString("value", itemId);
		KubeUINetworking.sendAction(KubeUIActions.RECIPE_QUERY_ITEM_ACTION, data);
	}

	static void receiveTypeResult(ListTag recipes) {
		var window = Minecraft.getInstance().getWindow();
		int contentHeight = clamp((int) (window.getGuiScaledHeight() * 0.6), 140, 360);

		var builder = KubeUIScreenBuilder.builder("Recipes")
			.draggable()
			.elementSize(300, 20)
			.windowBackground(KubeUIPanelTextures.RECIPE_BROWSER);

		builder.label("header", recipes.size() + " recipe" + (recipes.size() == 1 ? "" : "s") + " found");
		builder.divider();

		if (recipes.isEmpty()) {
			builder.label("empty", "No recipes found for that type.");
		} else {
			builder.scrollPanel(contentHeight, panel -> {
				for (var entry : recipes) {
					if (entry instanceof CompoundTag tag) {
						appendRecipeRow(panel, tag);
					}
				}
			});
		}

		builder.divider();
		builder.button("Close", ctx -> ctx.close());
		builder.open();
	}

	static void receiveItemResult(ListTag recipes) {
		var callback = pendingItemQuery;
		pendingItemQuery = null;
		if (callback == null) {
			return;
		}

		var results = new ArrayList<CompoundTag>();
		for (var entry : recipes) {
			if (entry instanceof CompoundTag tag) {
				results.add(tag);
			}
		}

		try {
			callback.accept(null, results);
		} catch (Exception ex) {
			KubeUI.LOGGER.error("KubeUI: error in a recipesFor(...) onResult callback", ex);
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static List<List<String>> parseInputs(CompoundTag tag) {
		var inputs = new ArrayList<List<String>>();
		for (var groupTag : tag.getListOrEmpty("inputs")) {
			var ids = new ArrayList<String>();
			if (groupTag instanceof ListTag group) {
				for (var idTag : group) {
					idTag.asString().ifPresent(ids::add);
				}
			}
			inputs.add(ids);
		}
		return inputs;
	}

	/// Just the path segment (after the last `:`) - a whole namespaced recipe id is usually too
	/// wide to show alongside the slots themselves without wrapping awkwardly.
	private static String shortId(String id) {
		int colon = id.lastIndexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	private static void appendRecipeRow(KubeUIScreenBuilder builder, CompoundTag tag) {
		String recipeId = tag.getStringOr("id", "recipe");
		var inputs = parseInputs(tag);
		String output = tag.getStringOr("output", "");

		builder.label(recipeId + "_name", shortId(recipeId));
		builder.row(row -> {
			for (int i = 0; i < inputs.size(); i++) {
				row.recipeSlot(recipeId + "_in" + i, inputs.get(i));
			}
			row.label(recipeId + "_arrow", inputs.isEmpty() ? "->" : "  ->  ").width(40);
			if (!output.isEmpty()) {
				row.recipeSlot(recipeId + "_out", List.of(output));
			}
		});
		builder.divider();
	}
}
