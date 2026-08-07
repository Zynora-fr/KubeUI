package dev.kubeui.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.kubeui.KubeUI;
import dev.latvian.mods.kubejs.KubeJSPaths;
import dev.latvian.mods.kubejs.core.MinecraftServerKJS;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Server-side logic for the in-game Recipe Designer (`/kubeui recipe-designer`) - a player picks
/// a recipe kind (crafting/furnace/blast furnace/smithing/...) and its ingredients/result through a
/// real crafting-grid-style GUI ([KubeUIRecipeGridMenu]), and taking the result writes real KubeJS
/// recipe-registration JS to `kubejs/server_scripts/kubeui_custom_recipes.js` - no custom
/// `Recipe`/`RecipeSerializer`/`RecipeType` registered in Java at all. Saving/deleting does *not*
/// trigger [#reload] automatically anymore - a full vanilla data-pack reload is expensive enough
/// (real lag spike) that doing it after every single save/delete while iterating on a recipe was a
/// real, reported problem; [#reload] is now only ever triggered by an explicit "Reload Recipes"
/// action from the designer screen, with a chat reminder after each save/delete in the meantime.
///
/// This design was chosen after decompiling this exact Minecraft version's real recipe API
/// (`Recipe<T>`/`RecipeSerializer`/`PlacementInfo`/`RecipeDisplay`) and finding it substantially
/// overhauled with several nested nested record types with no stable, simple "just construct one"
/// shape - registering a genuinely new Java-side recipe type safely would need much deeper
/// verification than this batch has room for. KubeJS's own scripting recipe API
/// (`ServerEvents.recipes(event => event.shapeless(...)/...)`), by contrast, is KubeJS's own
/// stable abstraction specifically meant to insulate scripts from that kind of Minecraft-version
/// churn - real, decompiled-verified positional argument shapes per recipe kind (see
/// [#toJs(KubeUIRecipeDef)]), not guessed from older-version tutorials.
///
/// Reload mechanism (also verified, not assumed): `ServerScriptManager#reload()` (`/kubejs reload
/// server`) alone does *not* make a newly-registered recipe live - it only re-runs scripts, recipe
/// reprocessing only actually happens as part of `RecipeManager`'s own vanilla reload listener,
/// which only runs on a real vanilla data-pack reload. KubeJS's own `/kubejs export` command
/// triggers exactly that afterward via `MinecraftServerKJS#kjs$runCommand("reload")` - [#reload]
/// does the same, the same real mechanism KubeJS itself already relies on for this.
final class KubeUIRecipeDesigner {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type LIST_TYPE = new TypeToken<List<KubeUIRecipeDef>>() {
	}.getType();
	private static final Path DATA_FILE = dev.latvian.mods.kubejs.KubeJSPaths.CONFIG.resolve("kubeui-custom-recipes.json");
	private static final Path SCRIPT_FILE = KubeJSPaths.SERVER_SCRIPTS.resolve("kubeui_custom_recipes.js");

	static final List<String> KINDS = List.of("shapeless", "shaped", "smelting", "blasting", "smoking", "stonecutting", "smithing");

	private static List<KubeUIRecipeDef> cache;

	private KubeUIRecipeDesigner() {
	}

	static List<KubeUIRecipeDef> list() {
		return new ArrayList<>(data());
	}

	/// Adds `def`, rewrites [#SCRIPT_FILE] from the full list, then reloads - `name` must be unique
	/// (case-insensitive), a duplicate is rejected rather than silently shadowing the earlier one.
	static boolean save(KubeUIRecipeDef def, ServerPlayer player) {
		var recipes = data();
		boolean duplicate = recipes.stream().anyMatch(r -> r.name().equalsIgnoreCase(def.name()));
		if (duplicate) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("A recipe named '" + def.name() + "' already exists - pick a different name or delete it first."));
			return false;
		}

		recipes.add(def);
		persist(recipes);
		regenerateScript(recipes);
		player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"Recipe '" + def.name() + "' saved - it won't be usable until you reload (Recipe Designer > Reload Recipes, or /reload)."));
		return true;
	}

	static void delete(String name, ServerPlayer player) {
		var recipes = data();
		recipes.removeIf(r -> r.name().equalsIgnoreCase(name));
		persist(recipes);
		regenerateScript(recipes);
		player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
			"Recipe '" + name + "' deleted - reload (Recipe Designer > Reload Recipes, or /reload) to apply it."));
	}

	private static List<KubeUIRecipeDef> data() {
		if (cache == null) {
			cache = load();
		}
		return cache;
	}

	private static List<KubeUIRecipeDef> load() {
		if (!Files.isRegularFile(DATA_FILE)) {
			return new ArrayList<>();
		}
		try (var reader = Files.newBufferedReader(DATA_FILE, StandardCharsets.UTF_8)) {
			List<KubeUIRecipeDef> loaded = GSON.fromJson(reader, LIST_TYPE);
			return loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
		} catch (Exception ex) {
			KubeUI.LOGGER.error("KubeUI: failed to read {} - starting with no saved custom recipes", DATA_FILE, ex);
			return new ArrayList<>();
		}
	}

	private static void persist(List<KubeUIRecipeDef> recipes) {
		cache = recipes;
		try {
			Files.createDirectories(DATA_FILE.getParent());
			try (var writer = Files.newBufferedWriter(DATA_FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(recipes, LIST_TYPE, writer);
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to write {}", DATA_FILE, ex);
		}
	}

	private static void regenerateScript(List<KubeUIRecipeDef> recipes) {
		var sb = new StringBuilder();
		sb.append("// Generated by the KubeUI Recipe Designer (/kubeui recipe-designer) - edits here\n");
		sb.append("// survive until the next save/delete from the designer, which regenerates this\n");
		sb.append("// whole file from its own saved list rather than editing it in place.\n\n");
		sb.append("ServerEvents.recipes(event => {\n");
		for (var def : recipes) {
			sb.append("    // ").append(def.name()).append('\n');
			sb.append("    ").append(toJs(def)).append('\n');
		}
		sb.append("})\n");

		try {
			Files.createDirectories(SCRIPT_FILE.getParent());
			Files.writeString(SCRIPT_FILE, sb.toString(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to write {}", SCRIPT_FILE, ex);
		}
	}

	/// Real, decompiled-verified positional argument shapes per KubeJS's own bundled recipe schema
	/// JSON (`event.shaped`/`.shapeless`/`.smelting`/etc. are schema-driven, not fixed Java method
	/// signatures - the schema JSON is the actual source of truth this mirrors):
	/// `shapeless(result, ingredients)`, the cooking family `smelting`/`blasting`/
	/// `smoking(result, ingredient, xp, cookingtime)`, `stonecutting(result, ingredient)`,
	/// `smithing(result, template, base, addition)`. One honest caveat: the exact literal grammar
	/// for a result count &gt; 1 (`"Nx modid:item"` here) wasn't traced down to verified bytecode,
	/// only confirmed as KubeJS's long-standing established convention - see
	/// [KubeUIRecipeDesigner]'s class doc.
	private static String toJs(KubeUIRecipeDef def) {
		String result = def.resultCount() > 1 ? quote(def.resultCount() + "x " + def.resultItem()) : quote(def.resultItem());
		var ingredients = def.ingredients();

		return switch (def.kind()) {
			case "shapeless" -> "event.shapeless(" + result + ", " + quoteList(ingredients) + ")";
			case "shaped" -> toShapedJs(result, ingredients);
			case "smelting", "blasting", "smoking" -> {
				String method = switch (def.kind()) {
					case "blasting" -> "blasting";
					case "smoking" -> "smoking";
					default -> "smelting";
				};
				String ingredient = ingredients.isEmpty() ? "''" : quote(ingredients.get(0));
				// A cooking recipe type only ever resolves ONE recipe per input item
				// (RecipeManager#getRecipeFor picks the first match) - if the ingredient already has
				// an existing smelting/blasting/smoking recipe (very likely for any vanilla item,
				// e.g. logs already smelt into charcoal), that existing recipe silently wins and
				// this one never runs, even though it registered successfully with no error. Real,
				// reproduced bug (not guessed): a custom smelting recipe for stripped_birch_log
				// registered fine but a real furnace kept producing charcoal instead, because
				// vanilla's log->charcoal recipe matches the same input and was found first.
				// event.remove({type, input}) (real KubeJS API, RecipeFilter) clears any existing
				// recipe of the same type for the same input first, so this one is always the one
				// that's actually used.
				String removeExisting = "event.remove({type: " + quote("minecraft:" + def.kind()) + ", input: " + ingredient + "})";
				yield removeExisting + "\n    event." + method + "(" + result + ", " + ingredient + ", " + def.xp() + ", " + def.cookTicks() + ")";
			}
			case "stonecutting" -> {
				String ingredient = ingredients.isEmpty() ? "''" : quote(ingredients.get(0));
				yield "event.stonecutting(" + result + ", " + ingredient + ")";
			}
			case "smithing" -> {
				String template = ingredients.size() > 0 && !ingredients.get(0).isBlank() ? quote(ingredients.get(0)) : null;
				String base = ingredients.size() > 1 ? quote(ingredients.get(1)) : "''";
				String addition = ingredients.size() > 2 ? quote(ingredients.get(2)) : "''";
				yield template != null
					? "event.smithing(" + result + ", " + template + ", " + base + ", " + addition + ")"
					: "event.smithing(" + result + ", " + base + ", " + addition + ")";
			}
			default -> "// unknown recipe kind '" + def.kind() + "', skipped";
		};
	}

	/// `ingredients` is exactly 9 entries, row-major (index `row * 3 + col`), `""` for an empty
	/// cell - the real shape [KubeUIRecipeDesignerGrid] captures from its 3x3 grid. Assigns each
	/// distinct item its own letter and builds the `pattern`/`key` real `event.shaped(...)` needs
	/// (verified real positional shape: `event.shaped(result, pattern, key)`).
	private static String toShapedJs(String result, List<String> ingredients) {
		var letters = new ArrayList<String>();
		for (String item : ingredients) {
			if (!item.isBlank() && !letters.contains(item)) {
				letters.add(item);
			}
		}

		var patternRows = new ArrayList<String>();
		for (int row = 0; row < 3; row++) {
			var sb = new StringBuilder();
			for (int col = 0; col < 3; col++) {
				String item = row * 3 + col < ingredients.size() ? ingredients.get(row * 3 + col) : "";
				sb.append(item.isBlank() ? ' ' : (char) ('A' + letters.indexOf(item)));
			}
			patternRows.add(sb.toString());
		}

		var keyEntries = new ArrayList<String>();
		for (int i = 0; i < letters.size(); i++) {
			keyEntries.add((char) ('A' + i) + ": " + quote(letters.get(i)));
		}

		return "event.shaped(" + result + ", " + quoteList(patternRows) + ", {" + String.join(", ", keyEntries) + "})";
	}

	private static String quote(String s) {
		return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
	}

	private static String quoteList(List<String> items) {
		var sb = new StringBuilder("[");
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(quote(items.get(i)));
		}
		return sb.append(']').toString();
	}

	/// The real vanilla data-pack reload (not `/kubejs reload server`, which alone doesn't
	/// reprocess `RecipeManager` - see the class doc) - the same mechanism `/kubejs export`
	/// already triggers internally after regenerating its own data. Only ever called from the
	/// designer's explicit "Reload Recipes" action now, not automatically after every save/delete.
	static void reload(ServerPlayer player) {
		var server = player.level().getServer();
		if (server instanceof MinecraftServerKJS serverKJS) {
			serverKJS.kjs$runCommand("reload");
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("kubeui_custom_recipes.js updated and reloaded."));
		} else {
			KubeUI.LOGGER.error("KubeUI: MinecraftServer didn't expose MinecraftServerKJS - couldn't trigger a reload automatically. Run /reload manually.");
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("kubeui_custom_recipes.js updated - run /reload manually to make it live (automatic reload failed, see log)."));
		}
	}

	static net.minecraft.nbt.ListTag listToTag(List<KubeUIRecipeDef> recipes) {
		var list = new net.minecraft.nbt.ListTag();
		for (var def : recipes) {
			list.add(toTag(def));
		}
		return list;
	}

	private static net.minecraft.nbt.CompoundTag toTag(KubeUIRecipeDef def) {
		var tag = new net.minecraft.nbt.CompoundTag();
		tag.putString("name", def.name());
		tag.putString("kind", def.kind());
		var ingredients = new net.minecraft.nbt.ListTag();
		def.ingredients().forEach(i -> ingredients.add(net.minecraft.nbt.StringTag.valueOf(i)));
		tag.put("ingredients", ingredients);
		tag.putString("resultItem", def.resultItem());
		tag.putInt("resultCount", def.resultCount());
		tag.putInt("cookTicks", def.cookTicks());
		tag.putFloat("xp", def.xp());
		return tag;
	}
}
