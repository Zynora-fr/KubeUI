package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Server-side registry of named actions a KubeUI screen can request via
/// `screen.runServerAction(id, data)`. Unlike [KubeUIContext#giveItem] (which just runs a
/// client-sent `/give` command, gated only by the player's own command permissions), an action
/// registered here runs entirely server-side - the handler decides what happens and whether it's
/// allowed, so this is safe to use for anything that must be trustworthy in normal survival
/// (a shop, a claim, anything with a cost). Bound as the `KubeUIActions` global in
/// `server_scripts`.
public final class KubeUIActions {
	private static final Map<String, KubeUIActionHandler> HANDLERS = new ConcurrentHashMap<>();
	private static final Map<String, Map<String, Byte>> SCHEMAS = new ConcurrentHashMap<>();
	private static final Map<String, Integer> THROTTLE_MS = new ConcurrentHashMap<>();
	private static final Map<UUID, Map<String, Long>> LAST_CALL_AT = new ConcurrentHashMap<>();
	private static final Map<UUID, String> OPEN_SCREENS = new ConcurrentHashMap<>();

	/// Reserved, non-namespaced action id `KubeUIScreen` sends automatically when a screen built
	/// with `.screenId(id)` opens/closes - see [#setOpenScreen]/[KubeUIContext#getOpenScreenId].
	/// Never registered as a real handler, so it never shows up as "unknown action" - intercepted
	/// directly in [KubeUINetworking].
	static final String SCREEN_STATE_ACTION = "__kubeui_screen_state";

	/// Reserved action id a screen sends automatically (batched, never per-widget) for every
	/// distinct `.requirePermission(gate)` it has - see [KubeUIPermissions]. Same "never a real
	/// handler" treatment as [#SCREEN_STATE_ACTION].
	static final String PERMISSION_CHECK_ACTION = "__kubeui_permission_check";

	/// Reserved `screenId` [KubeUIRemotePayload] carries the reply to [#PERMISSION_CHECK_ACTION]
	/// on - intercepted client-side in `KubeUINetworking` before falling through to
	/// `KubeUIRemoteScreens`, so a script's own `.register(...)` calls never see it.
	static final String PERMISSION_RESULT_SCREEN_ID = "__kubeui_permission_result";

	/// Reserved `screenId` carrying a [#setSidebarIconVisible] update - same interception
	/// convention as [#PERMISSION_RESULT_SCREEN_ID].
	static final String SIDEBAR_VISIBILITY_SCREEN_ID = "__kubeui_sidebar_visibility";

	/// Reserved action ids `KubeUI.recipeScreen(recipeTypeId)`/`.recipesFor(itemId, onResult)` send
	/// automatically - a real recipe lookup needs `MinecraftServer#getRecipeManager()`, only
	/// reachable server-side, so the client always asks and gets a reply rather than reading recipes
	/// itself (`Level#recipeAccess()` client-side only exposes the newer recipe-*display* sync, not
	/// a queryable recipe list - verified against the real decompiled class). Same "never a real
	/// handler" treatment as [#SCREEN_STATE_ACTION].
	static final String RECIPE_QUERY_TYPE_ACTION = "__kubeui_recipe_query_type";
	static final String RECIPE_QUERY_ITEM_ACTION = "__kubeui_recipe_query_item";

	/// Reserved `screenId`s carrying the reply to [#RECIPE_QUERY_TYPE_ACTION]/
	/// [#RECIPE_QUERY_ITEM_ACTION] - same interception convention as [#PERMISSION_RESULT_SCREEN_ID].
	static final String RECIPE_RESULT_TYPE_SCREEN_ID = "__kubeui_recipe_result_type";
	static final String RECIPE_RESULT_ITEM_SCREEN_ID = "__kubeui_recipe_result_item";

	/// Reserved action ids `/kubeui recipe-designer` sends - list the currently saved custom
	/// recipes, open a real crafting-grid GUI for a chosen kind ([KubeUIRecipeDesignerGrid], which
	/// saves directly once the player left-clicks its result slot - there's no separate "save"
	/// action from the KubeUI screen itself), delete an existing one, or explicitly trigger the
	/// real (expensive) data-pack reload that actually makes saved/deleted recipes live - no longer
	/// automatic after every save/delete, since that was a real, reported lag spike. Same "never a
	/// real handler, intercepted directly" treatment as the other reserved actions above.
	static final String RECIPE_DESIGNER_LIST_ACTION = "__kubeui_recipe_designer_list";
	static final String RECIPE_DESIGNER_OPEN_GRID_ACTION = "__kubeui_recipe_designer_open_grid";
	static final String RECIPE_DESIGNER_DELETE_ACTION = "__kubeui_recipe_designer_delete";
	static final String RECIPE_DESIGNER_RELOAD_ACTION = "__kubeui_recipe_designer_reload";

	/// Reserved `screenId` carrying the reply to [#RECIPE_DESIGNER_LIST_ACTION] (also sent again
	/// after a save/delete, so the screen can refresh itself with the up-to-date list).
	static final String RECIPE_DESIGNER_RESULT_SCREEN_ID = "__kubeui_recipe_designer_result";

	/// Reserved action ids `/kubeui trader-designer` sends - list the current in-progress trader
	/// (trades built so far this session, AI/movement flags), open the real item-slot GUI to
	/// physically define one more trade ([KubeUITraderGridMenu], same "captures on the result
	/// slot's left click" flow as the recipe designer's grid), remove one already-added trade,
	/// update the AI/movement flags, or bake everything onto a real [KubeUITraderEggItem] and give
	/// it to the player - see [KubeUITraderDesigner]. Same "never a real handler, intercepted
	/// directly" treatment as the other reserved actions above.
	static final String TRADER_DESIGNER_LIST_ACTION = "__kubeui_trader_designer_list";
	static final String TRADER_DESIGNER_OPEN_GRID_ACTION = "__kubeui_trader_designer_open_grid";
	static final String TRADER_DESIGNER_REMOVE_TRADE_ACTION = "__kubeui_trader_designer_remove_trade";
	static final String TRADER_DESIGNER_SET_FLAGS_ACTION = "__kubeui_trader_designer_set_flags";
	static final String TRADER_DESIGNER_GIVE_EGG_ACTION = "__kubeui_trader_designer_give_egg";

	/// Reserved `screenId` carrying the reply to [#TRADER_DESIGNER_LIST_ACTION] (also sent again
	/// after any change, so the screen can refresh itself with the up-to-date in-progress trader).
	static final String TRADER_DESIGNER_RESULT_SCREEN_ID = "__kubeui_trader_designer_result";

	/// Reserved action ids the quest system sends - `KubeUI.questLog()`/`/kubeui quest-log` asks
	/// for every known quest plus this player's progress on each; `.questTrack(questId)` sets
	/// (or, with an empty id, clears) which quest [KubeUIQuestEvents] pushes HUD progress for;
	/// accept/complete are sent from a quest-giver screen (never the log, which is read-only - see
	/// [KubeUIQuestGiverScreen]) along with the giver entity's UUID so the server can resolve it
	/// again and re-send a fresh list, the same "mutate then re-send" shape
	/// [#TRADER_DESIGNER_GIVE_EGG_ACTION] already uses. Same "never a real handler, intercepted
	/// directly" treatment as the other reserved actions above.
	static final String QUEST_LOG_REQUEST_ACTION = "__kubeui_quest_log_request";
	static final String QUEST_TRACK_ACTION = "__kubeui_quest_track";
	static final String QUEST_GIVER_ACCEPT_ACTION = "__kubeui_quest_giver_accept";
	static final String QUEST_GIVER_COMPLETE_ACTION = "__kubeui_quest_giver_complete";

	/// Reserved action ids `/quest accept <questId>`/`/quest complete <questId>` send - the same
	/// underlying [KubeUIQuests#accept]/[KubeUIQuests#complete] the quest-giver screen's buttons
	/// use, but for a player who wants to skip finding an actual NPC/block entirely. Unlike
	/// [#QUEST_GIVER_ACCEPT_ACTION] (silent - the giver screen re-rendering *is* the feedback),
	/// these reply with a real chat message, since there's no screen open to show the result
	/// otherwise.
	static final String QUEST_COMMAND_ACCEPT_ACTION = "__kubeui_quest_command_accept";
	static final String QUEST_COMMAND_COMPLETE_ACTION = "__kubeui_quest_command_complete";

	/// Reserved action ids `/kubeui quest-editor` sends - list quests the editor itself created
	/// (never a script's - editing a script-defined quest from the GUI would silently stop
	/// reflecting the script's own source on the next reload, a worse trap than just not allowing
	/// it), save (create or overwrite) one, delete one. Persisted for real between server restarts
	/// - see [KubeUIQuests#saveEditorQuests] - since, unlike a script, nothing else re-registers an
	/// editor-made quest on the next boot.
	static final String QUEST_EDITOR_LIST_ACTION = "__kubeui_quest_editor_list";
	static final String QUEST_EDITOR_SAVE_ACTION = "__kubeui_quest_editor_save";
	static final String QUEST_EDITOR_DELETE_ACTION = "__kubeui_quest_editor_delete";

	/// Reserved `screenId`s carrying quest-system replies - result-of-request for the log/editor
	/// (same convention as [#RECIPE_DESIGNER_RESULT_SCREEN_ID]/[#TRADER_DESIGNER_RESULT_SCREEN_ID]),
	/// a fresh quest-giver list after an interaction or an accept/complete, and a periodic HUD
	/// progress push (see [KubeUIQuestEvents#pushHudUpdate]) for whichever quest is tracked.
	static final String QUEST_LOG_RESULT_SCREEN_ID = "__kubeui_quest_log_result";
	static final String QUEST_EDITOR_RESULT_SCREEN_ID = "__kubeui_quest_editor_result";
	static final String QUEST_GIVER_RESULT_SCREEN_ID = "__kubeui_quest_giver_result";
	static final String QUEST_HUD_UPDATE_SCREEN_ID = "__kubeui_quest_hud_update";

	private KubeUIActions() {
	}

	/// Registers `id` (typically `"myaddon:action"` - see [#register(String, int, KubeUIActionHandler)]
	/// for the namespace convention this warns about) to run `handler` whenever a client requests it.
	public static void register(String id, KubeUIActionHandler handler) {
		auditNamespace(id);
		HANDLERS.put(id, handler);
	}

	/// Same as [#register(String, KubeUIActionHandler)], but a client requesting `id` again for the
	/// same player before `throttleMs` has passed since their last successful call is silently
	/// dropped (logged, never a crash) instead of reaching `handler` - a player mashing a button
	/// can't spam the server with packets faster than this.
	public static void register(String id, int throttleMs, KubeUIActionHandler handler) {
		register(id, handler);
		THROTTLE_MS.put(id, Math.max(0, throttleMs));
	}

	/// Same as [#register(String, KubeUIActionHandler)], but `data` is checked against `schema`
	/// (field name -> `"string"`/`"int"`/`"long"`/`"double"`/`"float"`/`"boolean"`/`"list"`/
	/// `"compound"`) before `handler` ever runs - a payload missing a required field or carrying
	/// the wrong NBT type for it is rejected (logged, never reaches the handler) instead of the
	/// handler having to defensively re-check every field itself.
	public static void register(String id, Map<String, String> schema, KubeUIActionHandler handler) {
		register(id, handler);
		SCHEMAS.put(id, parseSchema(id, schema));
	}

	/// Combines [#register(String, int, KubeUIActionHandler)] and
	/// [#register(String, Map, KubeUIActionHandler)] - both a throttle and a schema.
	public static void register(String id, Map<String, String> schema, int throttleMs, KubeUIActionHandler handler) {
		register(id, throttleMs, handler);
		SCHEMAS.put(id, parseSchema(id, schema));
	}

	private static Map<String, Byte> parseSchema(String id, Map<String, String> schema) {
		var parsed = new HashMap<String, Byte>();
		if (schema != null) {
			schema.forEach((field, typeName) -> {
				Byte tagType = tagTypeOf(typeName);
				if (tagType == null) {
					KubeUI.LOGGER.error("KubeUIActions.register('{}', ...) - unknown schema type '{}' for field '{}', ignoring that field (never rejects on it)", id, typeName, field);
				} else {
					parsed.put(field, tagType);
				}
			});
		}
		return parsed;
	}

	private static Byte tagTypeOf(String name) {
		return switch (name) {
			case "string" -> Tag.TAG_STRING;
			case "int" -> Tag.TAG_INT;
			case "long" -> Tag.TAG_LONG;
			case "double" -> Tag.TAG_DOUBLE;
			case "float" -> Tag.TAG_FLOAT;
			case "boolean", "byte" -> Tag.TAG_BYTE;
			case "list" -> Tag.TAG_LIST;
			case "compound" -> Tag.TAG_COMPOUND;
			default -> null;
		};
	}

	/// A namespace-less id (no `:`) isn't rejected - only warned about - since KubeUI has no way to
	/// tell "a script's own convention" from "a mistake", but two independent addons both picking
	/// e.g. `"buy"` would silently collide (the second `register` call overwrites the first).
	private static void auditNamespace(String id) {
		if (id != null && !id.contains(":")) {
			KubeUI.LOGGER.warn("KubeUIActions.register('{}', ...) - no namespace ('mymod:{}') - fine for a single addon, but risks silently colliding with another one registering the same plain id.", id, id);
		}
	}

	static KubeUIActionHandler get(String id) {
		return HANDLERS.get(id);
	}

	/// Returns null (valid) or a human-readable reason `data` fails the schema registered for `id`
	/// (see [#register(String, Map, KubeUIActionHandler)]) - no schema registered always validates.
	static String validate(String id, CompoundTag data) {
		var schema = SCHEMAS.get(id);
		if (schema == null || schema.isEmpty()) {
			return null;
		}

		for (var entry : schema.entrySet()) {
			Tag tag = data.get(entry.getKey());
			if (tag == null) {
				return "missing required field '" + entry.getKey() + "'";
			}
			if (tag.getId() != entry.getValue()) {
				return "field '" + entry.getKey() + "' has the wrong type";
			}
		}
		return null;
	}

	/// Returns true (and records this call as the new "last call") if `id` for `player` is *not*
	/// currently throttled; returns false without recording anything if it is - see
	/// [#register(String, int, KubeUIActionHandler)].
	static boolean tryConsumeThrottle(ServerPlayer player, String id) {
		Integer minIntervalMs = THROTTLE_MS.get(id);
		if (minIntervalMs == null || minIntervalMs <= 0) {
			return true;
		}

		long now = System.currentTimeMillis();
		var perAction = LAST_CALL_AT.computeIfAbsent(player.getUUID(), ignored -> new ConcurrentHashMap<>());
		Long last = perAction.get(id);
		if (last != null && now - last < minIntervalMs) {
			return false;
		}
		perAction.put(id, now);
		return true;
	}

	/// Opens `screenId` (see `KubeUIRemoteScreens.register` client-side) for `player`, with `data`
	/// handed to whichever handler that client registered for it. Fire-and-forget - the server has
	/// no way to know whether the client actually had a matching handler registered.
	public static void openRemote(ServerPlayer player, String screenId, CompoundTag data) {
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(screenId, data == null ? new CompoundTag() : data));
	}

	/// Same as [#openRemote], sent to every connected player at once - for screens meant to update
	/// live and identically for everyone (a shared scoreboard, a server-wide announcement screen).
	public static void broadcastUpdate(String screenId, CompoundTag data) {
		PacketDistributor.sendToAllPlayers(new KubeUIRemotePayload(screenId, data == null ? new CompoundTag() : data));
	}

	/// Shows/hides a `KubeUISidebar` icon (registered client-side via `.addItem`/`.addTexture`) for
	/// one specific player - e.g. an "Admin" icon only actually shown once the server confirms this
	/// player is one, rather than every client deciding for itself. `iconId` is the same id the
	/// icon was registered under; a client that never registered it simply ignores the update.
	public static void setSidebarIconVisible(ServerPlayer player, String iconId, boolean visible) {
		var data = new CompoundTag();
		data.putString("iconId", iconId);
		data.putBoolean("visible", visible);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(SIDEBAR_VISIBILITY_SCREEN_ID, data));
	}

	/// Records/clears which `.screenId(id)`-tagged KubeUI screen `player` currently has open -
	/// called automatically by `KubeUIScreen` on open/close via the reserved
	/// [#SCREEN_STATE_ACTION], never by scripts directly.
	static void setOpenScreen(ServerPlayer player, String screenId) {
		if (screenId == null) {
			OPEN_SCREENS.remove(player.getUUID());
		} else {
			OPEN_SCREENS.put(player.getUUID(), screenId);
		}
	}

	/// Clears whatever open-screen and per-action throttle state was recorded for a player who just
	/// disconnected, so neither lingers forever - called from `KubeUINetworking`'s disconnect
	/// handling. [#LAST_CALL_AT] not being cleared here was a real gap found during a security/
	/// robustness audit - unlike [#OPEN_SCREENS] (already cleared), it kept every
	/// distinct player UUID that ever called a throttled action for the life of the server.
	static void clearOpenScreen(UUID playerId) {
		OPEN_SCREENS.remove(playerId);
		LAST_CALL_AT.remove(playerId);
	}

	/// The id of whatever `.screenId(id)`-tagged KubeUI screen `player` currently has open on their
	/// client, or null if none (either nothing is open, or it wasn't tagged with `.screenId(...)` -
	/// screens are opt-in to this tracking, not tracked by default). Reflects what the client last
	/// reported, not a live poll - accurate as of the last open/close on that client.
	public static String getOpenScreenId(ServerPlayer player) {
		return OPEN_SCREENS.get(player.getUUID());
	}

	/// A `CompoundTag` that's actually saved with `player`'s own data and survives reconnects/server
	/// restarts (backed by [net.minecraft.world.entity.Entity#getPersistentData]) - for a script
	/// that wants to remember some state per player without building its own save file. Returns the
	/// *live* tag (not a copy): mutate it directly (`data.putInt(...)`, etc.) rather than replacing
	/// the whole object.
	public static CompoundTag playerData(ServerPlayer player) {
		var root = player.getPersistentData();
		return root.getCompound("kubeui").orElseGet(() -> {
			var created = new CompoundTag();
			root.put("kubeui", created);
			return created;
		});
	}

	/// Registers a named pool of weighted trades - `trades` is a list of `{id, weight, costs,
	/// resultItem, resultCount, maxUses, restockTicks}` objects (`costs` itself a list of
	/// `{item, count}`), the same JS-object-list shape already proven working for
	/// `KubeUI.configScreen(...)`'s schema parameter. Tag an entity with this pool via
	/// [#tagTradePool] to make it offer these trades - see [KubeUIVillagerTrades] for why this
	/// doesn't integrate with vanilla's own (in this Minecraft version, heavily overhauled)
	/// `VillagerTrades`/`MerchantOffer` system.
	public static void registerTradePool(String poolId, List<Map<String, Object>> trades, KubeUITradeCondition condition) {
		var defs = new ArrayList<KubeUITradeDef>();
		for (var trade : trades) {
			String id = String.valueOf(trade.get("id"));
			int weight = trade.get("weight") instanceof Number n ? Math.max(1, n.intValue()) : 1;
			String resultItem = String.valueOf(trade.get("resultItem"));
			int resultCount = trade.get("resultCount") instanceof Number n ? Math.max(1, n.intValue()) : 1;
			int maxUses = trade.get("maxUses") instanceof Number n ? Math.max(1, n.intValue()) : 12;
			int restockTicks = trade.get("restockTicks") instanceof Number n ? Math.max(0, n.intValue()) : 24000;

			var costs = new ArrayList<KubeUITradeCost>();
			if (trade.get("costs") instanceof List<?> costList) {
				for (var costEntry : costList) {
					if (costEntry instanceof Map<?, ?> costMap) {
						String item = String.valueOf(costMap.get("item"));
						int count = costMap.get("count") instanceof Number n ? Math.max(1, n.intValue()) : 1;
						costs.add(new KubeUITradeCost(item, count));
					}
				}
			}

			defs.add(new KubeUITradeDef(id, weight, costs, resultItem, resultCount, maxUses, restockTicks));
		}
		KubeUIVillagerTrades.registerPool(poolId, defs, condition);
	}

	/// Same as [#registerTradePool(String, List, KubeUITradeCondition)], with no reputation/quest
	/// gating - every trade in the pool is always eligible to be rolled/shown.
	public static void registerTradePool(String poolId, List<Map<String, Object>> trades) {
		registerTradePool(poolId, trades, null);
	}

	/// Marks `entity` as offering the trades in `poolId` when right-clicked - see
	/// [KubeUIVillagerTradeInteraction]. Works on any entity, not just villagers (nothing here
	/// checks the entity's type) - KubeJS has no scripting hook to register a genuinely new entity
	/// type in this version, so reusing an existing one is the real way to get a "merchant that
	/// doesn't have to be a real villager".
	public static void tagTradePool(Entity entity, String poolId) {
		KubeUIVillagerTrades.tagPool(entity, poolId);
	}

	/// Every trade id `player` has ever completed, oldest first - a trade completed more than once
	/// appears more than once. Backed by the player's own persistent data, survives reconnects.
	public static List<String> tradeHistory(ServerPlayer player) {
		return KubeUIVillagerTrades.tradeHistory(player);
	}

	/// Backs `/kubeui villager-trades <target>` - a human-readable summary of an entity's current
	/// trade pool/offers/stock, for inspecting what's actually been rolled without reading NBT by
	/// hand. `viewer` matters because trades can be condition-gated per player.
	public static String describeTrades(Entity entity, ServerPlayer viewer) {
		return KubeUIVillagerTrades.describe(entity, viewer);
	}

	/// Declares a quest (re-registered every server boot, same reasoning as
	/// [#registerTradePool]) - `data` is a plain JS object:
	/// ```js
	/// KubeUI.defineQuest('gather_wood', {
	///   title: 'Gather Wood', description: 'Collect 10 oak logs.',
	///   requires: ['some_other_quest_id'],               // optional, default none
	///   objectives: [
	///     { type: 'collect', item: 'minecraft:oak_log', target: 10 },
	///     { type: 'kill', entity: 'minecraft:zombie', target: 5, label: 'Zombies slain' },
	///     { type: 'visit', dimension: 'minecraft:overworld', x: 100, y: 64, z: 200, radius: 10 },
	///     { type: 'visit', structureTag: 'minecraft:village' },
	///     { type: 'xpLevel', target: 5 },
	///     { type: 'break_stone', id: 'stone', target: 20 },   // custom - see below
	///   ],
	///   rewards: [
	///     { type: 'item', item: 'minecraft:emerald', count: 3 },
	///     { type: 'xp', levels: 1 },
	///     { type: 'command', command: 'say @s completed a quest!' },
	///   ],
	/// });
	/// ```
	/// `collect`/`xpLevel` progress is always computed live from the player's actual current state
	/// (inventory contents / XP level) - never stored, never goes stale. `kill`/`visit` (and any
	/// objective `type` this class doesn't recognize - the "generic hook" for a scripter's own
	/// objective kind) are backed by a real, persisted counter a player's own actions bump: `kill`
	/// bumps itself automatically (see [KubeUIQuestEvents#onLivingDeath]), `visit` flips itself once
	/// the player's real position/structure matches (checked periodically, see
	/// [KubeUIQuestEvents#checkVisitObjectives]), and any other type is bumped by the script itself
	/// via [#incrementQuestObjective] from whatever event the script cares about (a block break, a
	/// custom advancement, anything) - no callback registration needed, just call it from your own
	/// listener.
	public static void defineQuest(String id, Map<String, Object> data) {
		String title = stringOrDefault(data.get("title"), id);
		String description = stringOrDefault(data.get("description"), "");

		var requires = new ArrayList<String>();
		if (data.get("requires") instanceof List<?> requiresList) {
			for (var entry : requiresList) {
				requires.add(String.valueOf(entry));
			}
		}

		var objectives = new ArrayList<KubeUIQuestObjective>();
		if (data.get("objectives") instanceof List<?> objectivesList) {
			int index = 0;
			for (var entry : objectivesList) {
				if (entry instanceof Map<?, ?> objectiveMap) {
					objectives.add(parseObjective(objectiveMap, index));
				}
				index++;
			}
		}

		var rewards = new ArrayList<KubeUIQuestReward>();
		if (data.get("rewards") instanceof List<?> rewardsList) {
			for (var entry : rewardsList) {
				if (entry instanceof Map<?, ?> rewardMap) {
					rewards.add(parseReward(rewardMap));
				}
			}
		}

		KubeUIQuests.defineQuest(new KubeUIQuestDef(id, title, description, requires, objectives, rewards, "script"));
	}

	private static KubeUIQuestObjective parseObjective(Map<?, ?> map, int index) {
		String type = String.valueOf(map.get("type"));
		String id = blankToDefault(stringOrDefault(map.get("id"), ""), "obj_" + index);
		int target = map.get("target") instanceof Number n ? Math.max(1, n.intValue()) : 1;

		var data = new CompoundTag();
		switch (type) {
			case "collect" -> data.putString("item", String.valueOf(map.get("item")));
			case "kill" -> data.putString("entity", String.valueOf(map.get("entity")));
			case "visit" -> {
				String structureTag = stringOrDefault(map.get("structureTag"), "");
				if (!structureTag.isBlank()) {
					data.putString("structureTag", structureTag);
				} else {
					data.putString("dimension", blankToDefault(stringOrDefault(map.get("dimension"), ""), "minecraft:overworld"));
					data.putDouble("x", map.get("x") instanceof Number n ? n.doubleValue() : 0);
					data.putDouble("y", map.get("y") instanceof Number n ? n.doubleValue() : 0);
					data.putDouble("z", map.get("z") instanceof Number n ? n.doubleValue() : 0);
					data.putDouble("radius", map.get("radius") instanceof Number n ? n.doubleValue() : 5);
				}
			}
			default -> {
				// "xpLevel" needs nothing beyond `target`; any other (custom) type is a plain
				// script-driven counter and needs nothing built-in either.
			}
		}

		String label = blankToDefault(stringOrDefault(map.get("label"), ""), autoObjectiveLabel(type, map));
		return new KubeUIQuestObjective(id, type, label, target, data);
	}

	/// `instanceof String` pattern matching on a raw script-object map value was tried and verified
	/// (headless, by temporarily logging the raw vs. parsed objective id at script-load time) to
	/// actually work fine against this KubeJS/Rhino setup's string values - not the failure mode it
	/// looked like it could be. Switched to `String.valueOf(...)` anyway (unconditional, via
	/// `toString()`) purely to match the one already-proven-correct convention every other script-
	/// object field in this codebase uses ([#registerTradePool]'s parsing, in particular) rather
	/// than a second, subtly different pattern with no real benefit over it.
	private static String stringOrDefault(Object value, String fallback) {
		return value != null ? String.valueOf(value) : fallback;
	}

	private static String blankToDefault(String value, String fallback) {
		return value.isBlank() ? fallback : value;
	}

	private static String autoObjectiveLabel(String type, Map<?, ?> map) {
		return switch (type) {
			case "collect" -> shortId(String.valueOf(map.get("item")));
			case "kill" -> "Kill " + shortId(String.valueOf(map.get("entity")));
			case "visit" -> "Visit location";
			case "xpLevel" -> "Reach XP level";
			default -> "Objective";
		};
	}

	private static String shortId(String id) {
		int colon = id.indexOf(':');
		return (colon >= 0 ? id.substring(colon + 1) : id).replace('_', ' ');
	}

	private static KubeUIQuestReward parseReward(Map<?, ?> map) {
		String type = String.valueOf(map.get("type"));
		var data = new CompoundTag();
		switch (type) {
			case "item" -> {
				data.putString("item", String.valueOf(map.get("item")));
				data.putInt("count", map.get("count") instanceof Number n ? Math.max(1, n.intValue()) : 1);
			}
			case "xp" -> data.putInt("levels", map.get("levels") instanceof Number n ? Math.max(0, n.intValue()) : 1);
			case "command" -> data.putString("command", String.valueOf(map.get("command")));
			default -> {
			}
		}
		return new KubeUIQuestReward(type, data);
	}

	/// The generic progress hook for any objective `type` [#defineQuest] doesn't build in itself
	/// (`"collect"`/`"kill"`/`"visit"`/`"xpLevel"`) - bump it from whatever server-side event the
	/// script cares about (a block break, a custom advancement trigger, anything). A no-op unless
	/// the quest is currently `"active"` for this player, so grinding before accepting (or after
	/// turning in) can never pre-fill/overfill an objective's counter.
	public static void incrementQuestObjective(ServerPlayer player, String questId, String objectiveId, int amount) {
		KubeUIQuests.incrementObjectiveCounter(player, questId, objectiveId, amount);
	}

	/// Marks `entity` as a quest giver for one or more quest ids - right-clicking it opens a real
	/// Accept/Turn-in screen for whichever of those quests currently apply (see
	/// [KubeUIQuestGiverInteraction]). Works on any entity, same reasoning as [#tagTradePool].
	/// Replaces `entity`'s whole quest-giver list rather than adding to it - tagging the same
	/// entity a second time with a different list drops the first one, it doesn't merge them.
	public static void tagQuestGiver(Entity entity, List<String> questIds) {
		KubeUIQuests.tagQuestGiver(entity, questIds);
	}

	/// `"not_started"`, `"active"`, or `"completed"` - for a script that wants to gate its own
	/// logic (a trade condition, a locked door, anything) on a player's quest progress without
	/// going through the built-in quest log/quest-giver screens at all.
	public static String questState(ServerPlayer player, String questId) {
		String state = KubeUIQuests.state(player, questId);
		return state.isEmpty() ? "not_started" : state;
	}
}
