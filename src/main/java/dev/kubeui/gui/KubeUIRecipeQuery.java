package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/// Server-side backing for `KubeUI.recipeScreen(recipeTypeId)`/`.recipesFor(itemId, onResult)` -
/// real recipes only exist queryably server-side (`MinecraftServer#getRecipeManager()`; the
/// client's own `Level#recipeAccess()` only exposes the newer recipe-*display* sync used by the
/// vanilla recipe book, not a queryable recipe list - verified against the real decompiled class),
/// so both script APIs are a `runServerAction`/reply round trip under the hood (see
/// [KubeUINetworking]), not a local lookup.
///
/// Generic across recipe type (works for a script's own custom schema *and* vanilla recipes alike)
/// via `Recipe#placementInfo().ingredients()` for inputs and `Recipe#display()`'s first
/// `RecipeDisplay#result()` resolved to a real `ItemStack` for the output - both real, type-agnostic
/// members of the `Recipe<T>` interface in this Minecraft version, not something KubeUI reimplements
/// per recipe shape. Each ingredient's *whole* accepted-items group is included (not just one
/// representative item), matching what `.recipeSlot(id, itemIds, onClick)` needs to
/// show a real "any of these" preview instead of picking one arbitrarily.
final class KubeUIRecipeQuery {
	private KubeUIRecipeQuery() {
	}

	/// Every recipe whose `getType()` matches `recipeTypeId` (an unknown/unresolvable id yields an
	/// empty result, not an error - the reply screen just shows nothing rather than needing its own
	/// failure state).
	static ListTag queryByType(ServerPlayer player, String recipeTypeId) {
		var identifier = Identifier.tryParse(recipeTypeId);
		RecipeType<?> type = identifier != null ? BuiltInRegistries.RECIPE_TYPE.getValue(identifier) : null;
		if (type == null) {
			return new ListTag();
		}

		var level = player.level();
		var recipeManager = level.getServer().getRecipeManager();
		var context = SlotDisplayContext.fromLevel(level);

		var results = new ListTag();
		for (var holder : recipeManager.getRecipes()) {
			if (holder.value().getType() == type) {
				results.add(describeRecipe(holder, context));
			}
		}
		return results;
	}

	/// Every recipe with at least one ingredient slot that accepts `itemId` - across *every*
	/// registered recipe type (not just one), matching a JEI-style "what can this become" query.
	static ListTag queryByItem(ServerPlayer player, String itemId) {
		var identifier = Identifier.tryParse(itemId);
		var item = identifier != null ? BuiltInRegistries.ITEM.getValue(identifier) : null;
		if (item == null) {
			return new ListTag();
		}

		var stack = new net.minecraft.world.item.ItemStack(item);
		var level = player.level();
		var recipeManager = level.getServer().getRecipeManager();
		var context = SlotDisplayContext.fromLevel(level);

		var results = new ListTag();
		for (var holder : recipeManager.getRecipes()) {
			boolean matches = holder.value().placementInfo().ingredients().stream().anyMatch(ing -> ing.test(stack));
			if (matches) {
				results.add(describeRecipe(holder, context));
			}
		}
		return results;
	}

	private static CompoundTag describeRecipe(net.minecraft.world.item.crafting.RecipeHolder<?> holder, net.minecraft.util.context.ContextMap context) {
		var tag = new CompoundTag();
		tag.putString("id", holder.id().identifier().toString());

		var inputs = new ListTag();
		for (var ingredient : holder.value().placementInfo().ingredients()) {
			var group = new ListTag();
			ingredient.items().forEach(itemHolder -> group.add(StringTag.valueOf(BuiltInRegistries.ITEM.getKey(itemHolder.value()).toString())));
			inputs.add(group);
		}
		tag.put("inputs", inputs);

		var displays = holder.value().display();
		if (!displays.isEmpty()) {
			var output = displays.get(0).result().resolveForFirstStack(context);
			if (!output.isEmpty()) {
				tag.putString("output", BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
			}
		}

		return tag;
	}
}
