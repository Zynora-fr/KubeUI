package dev.kubeui.gui;

import java.util.List;

/// One recipe defined through the in-game Recipe Designer (`/kubeui recipe-designer`) - plain
/// data, persisted as JSON (see [KubeUIRecipeDesigner]) and turned into real KubeJS
/// recipe-registration JS text, never a custom `Recipe`/`RecipeSerializer` registered in Java -
/// see [KubeUIRecipeDesigner]'s class doc for why.
///
/// `ingredients` means different things per `kind`, since the underlying KubeJS recipe schemas
/// take different shapes (verified against the real bundled schema JSON, not assumed): for
/// `"shapeless"` it's every ingredient item id; for the cooking family (`"smelting"`/`"blasting"`/
/// `"smoking"`/`"campfire_cooking"`) and `"stonecutting"` only the first entry is used (one
/// ingredient); for `"smithing"` it's `[template, base, addition]` (`template` may be blank, which
/// defaults to the netherite upgrade template per KubeJS's own `smithing_transform` schema
/// constructor overload).
record KubeUIRecipeDef(
	String name,
	String kind,
	List<String> ingredients,
	String resultItem,
	int resultCount,
	int cookTicks,
	float xp
) {
}
