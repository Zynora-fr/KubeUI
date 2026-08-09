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

	/// Reserved action ids `KubeUI.currencyHistory(currency)`/`.leaderboard(currency)`/`.shop(shopId)`
	/// send - same "ask the server, open once it replies" shape as [#RECIPE_QUERY_TYPE_ACTION]
	/// (balances/prices are real server state, never client-trusted). Buy/sell are two more
	/// reserved actions rather than a script's own `register(...)` handler, since they're a built-in
	/// KubeUI system (see [KubeUIShop]) - same treatment as the recipe/trader/quest designers above.
	static final String CURRENCY_HISTORY_ACTION = "__kubeui_currency_history";
	static final String CURRENCY_HISTORY_RESULT_SCREEN_ID = "__kubeui_currency_history_result";
	static final String LEADERBOARD_ACTION = "__kubeui_leaderboard";
	static final String LEADERBOARD_RESULT_SCREEN_ID = "__kubeui_leaderboard_result";
	static final String SHOP_OPEN_ACTION = "__kubeui_shop_open";
	static final String SHOP_BUY_ACTION = "__kubeui_shop_buy";
	static final String SHOP_SELL_ACTION = "__kubeui_shop_sell";
	static final String SHOP_RESULT_SCREEN_ID = "__kubeui_shop_result";

	/// Reserved action id `KubeUI.dialogue(dialogueId, npcEntityUuid)`/a dialogue choice send - same
	/// "ask the server, open/refresh once it replies" shape as the currency actions above. The
	/// server always resolves the *next* node from its own [KubeUIDialogue] definition (keyed by
	/// the choice index the client sends), never from a node id the client could have sent instead -
	/// same trust boundary as `KubeUIActions.completeQuest` never trusting a client-reported result.
	static final String DIALOGUE_OPEN_ACTION = "__kubeui_dialogue_open";
	static final String DIALOGUE_CHOOSE_ACTION = "__kubeui_dialogue_choose";
	static final String DIALOGUE_RESULT_SCREEN_ID = "__kubeui_dialogue_result";

	/// Reserved action ids [KubeUIStorageScreen]/the storage demo send - every one of these acts on
	/// "whatever storage this player currently has open" ([KubeUIStorageSessions]), never on a
	/// client-supplied position, so a malicious client can't point a sort/filter/lock action at a
	/// container it was never actually looking at.
	static final String STORAGE_SORT_ACTION = "__kubeui_storage_sort";
	static final String STORAGE_SET_FILTER_ACTION = "__kubeui_storage_set_filter";
	static final String STORAGE_TOGGLE_LOCK_ACTION = "__kubeui_storage_toggle_lock";
	static final String STORAGE_ADD_AUTHORIZED_ACTION = "__kubeui_storage_add_authorized";
	static final String STORAGE_REMOVE_AUTHORIZED_ACTION = "__kubeui_storage_remove_authorized";
	static final String STORAGE_SET_NETWORK_ACTION = "__kubeui_storage_set_network";
	static final String STORAGE_HISTORY_REQUEST_ACTION = "__kubeui_storage_history_request";
	static final String STORAGE_HISTORY_RESULT_SCREEN_ID = "__kubeui_storage_history_result";
	/// Also backs the "remote preview" - a player calling `KubeUI.storageNetworkView(id)`
	/// from anywhere (not necessarily standing at a member container) *is* exactly "see a linked
	/// container's contents without opening it physically, given permanent (authorized) access" -
	/// one real implementation for both cases rather than two near-identical ones.
	static final String STORAGE_NETWORK_VIEW_ACTION = "__kubeui_storage_network_view";
	static final String STORAGE_NETWORK_RESULT_SCREEN_ID = "__kubeui_storage_network_result";

	/// Reserved action ids `KubeUI.skillTree(id)`/`.skillLeaderboard(id)` send - same "ask the
	/// server, open once it replies" shape as every other server-authoritative screen above.
	static final String SKILL_TREE_REQUEST_ACTION = "__kubeui_skill_tree_request";
	static final String SKILL_TREE_RESULT_SCREEN_ID = "__kubeui_skill_tree_result";
	static final String SKILL_UNLOCK_NODE_ACTION = "__kubeui_skill_unlock_node";
	static final String SKILL_RESPEC_ACTION = "__kubeui_skill_respec";
	static final String SKILL_LEADERBOARD_ACTION = "__kubeui_skill_leaderboard";
	static final String SKILL_LEADERBOARD_RESULT_SCREEN_ID = "__kubeui_skill_leaderboard_result";

	/// Reserved action id `KubeUI.shareWaypoint(waypointId, targetPlayerName)` sends -
	/// the server only ever resolves `targetPlayerName` to a real online player and forwards the
	/// payload as-is, it never stores it (waypoints stay client-persisted, see [KubeUIWaypoints]).
	static final String WAYPOINT_SHARE_ACTION = "__kubeui_waypoint_share";
	static final String WAYPOINT_SHARE_RESULT_SCREEN_ID = "__kubeui_waypoint_share_result";

	/// Reserved `screenId` [KubeUIMachineAlerts] pushes an alert on - see its own doc.
	static final String MACHINE_ALERT_SCREEN_ID = "__kubeui_machine_alert";

	/// Reserved action ids `KubeUI.machineNetworkStatus(networkId)` sends - same "ask the server,
	/// open once it replies" shape as every other cross-machine screen in this mod.
	static final String MACHINE_NETWORK_STATUS_ACTION = "__kubeui_machine_network_status";
	static final String MACHINE_NETWORK_STATUS_RESULT_SCREEN_ID = "__kubeui_machine_network_status_result";

	/// Reserved `screenId`s for the combat HUD systems ([KubeUIBossBar]/[KubeUIStatusEffects]/
	/// [KubeUICooldowns]/[KubeUICombatLog]/[KubeUIAoeIndicator]) - each pushes its own state to its
	/// own client-side `Hud` cache class, same interception convention as every reserved id above.
	static final String BOSS_BAR_UPDATE_SCREEN_ID = "__kubeui_bossbar_update";
	static final String STATUS_UPDATE_SCREEN_ID = "__kubeui_status_update";
	static final String COOLDOWN_UPDATE_SCREEN_ID = "__kubeui_cooldown_update";
	static final String COMBAT_LOG_UPDATE_SCREEN_ID = "__kubeui_combatlog_update";
	static final String AOE_INDICATOR_UPDATE_SCREEN_ID = "__kubeui_aoe_indicator_update";

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

	// ---------------------------------------------------------------- economy

	/// Declares `currency` known even before anyone's balance changes it - not strictly required
	/// ([#pay] auto-registers it too), but useful for a leaderboard/admin tool that wants to list
	/// every currency a server defines even if nobody has earned any of one yet.
	public static void registerCurrency(String currency) {
		KubeUICurrency.registerCurrency(currency);
	}

	/// Every currency any script has registered/paid into so far - lets `/money balance` (with no
	/// currency given) report every balance a player actually has instead of forcing them to know
	/// and type an exact currency id first.
	public static java.util.Set<String> knownCurrencies() {
		return KubeUICurrency.knownCurrencies();
	}

	public static long balance(ServerPlayer player, String currency) {
		return KubeUICurrency.balance(player, currency);
	}

	/// Credits `player` - see [KubeUICurrency#pay].
	public static boolean pay(ServerPlayer player, String currency, long amount) {
		return KubeUICurrency.pay(player, currency, amount);
	}

	/// Debits `player` - fails cleanly (never a silent negative balance) if they can't afford it -
	/// see [KubeUICurrency#charge].
	public static boolean charge(ServerPlayer player, String currency, long amount) {
		return KubeUICurrency.charge(player, currency, amount);
	}

	/// Atomic player-to-player transfer, minus whatever [#setCurrencyTransferTax] configured for
	/// `currency` - see [KubeUICurrency#transfer].
	public static boolean transferCurrency(ServerPlayer from, ServerPlayer to, String currency, long amount) {
		return KubeUICurrency.transfer(from, to, currency, amount);
	}

	/// Sets the commission [#transferCurrency] deducts for `currency`, as a `0.0`-`1.0` fraction
	/// (e.g. `0.05` for a 5% cut) - `0` (the default for any currency never configured) means no
	/// tax at all. Re-declare on every script load, the same "no file persistence, scripts are the
	/// source of truth" convention every other rule-registration method on this class already uses.
	public static void setCurrencyTransferTax(String currency, double rate) {
		KubeUICurrency.setTransferTaxRate(currency, rate);
	}

	/// Removes `itemCount` of `itemId` from `player`'s real inventory and credits `currencyAmount` -
	/// fails cleanly if they don't actually have that many. See [KubeUICurrency#exchangeItemForCurrency].
	public static boolean exchangeItemForCurrency(ServerPlayer player, String itemId, int itemCount, String currency, long currencyAmount) {
		return KubeUICurrency.exchangeItemForCurrency(player, itemId, itemCount, currency, currencyAmount);
	}

	/// The reverse of [#exchangeItemForCurrency] - charges first, fails cleanly if `player` can't
	/// afford it, otherwise gives the item. See [KubeUICurrency#exchangeCurrencyForItem].
	public static boolean exchangeCurrencyForItem(ServerPlayer player, String currency, long currencyAmount, String itemId, int itemCount) {
		return KubeUICurrency.exchangeCurrencyForItem(player, currency, currencyAmount, itemId, itemCount);
	}

	/// Declares a shop (re-declared every server boot, same convention as [#registerTradePool]) -
	/// `stock` is a list of `{id, item, count, currency, price, sellable, sellRate, stock, minPrice,
	/// maxPrice, fluctuationIntervalTicks}` objects, the same JS-object-list shape `#registerTradePool`
	/// already uses. `stock: -1` (the default) means an unlimited buy line; `minPrice`/`maxPrice` both
	/// set enables periodic price fluctuation for that one line, left `null` (the
	/// default - simply omit them) for a fixed price. Open it from a script with `KubeUI.shop(shopId)`.
	public static void defineShop(String shopId, String displayName, List<Map<String, Object>> stock) {
		var parsed = new ArrayList<KubeUIShopStock>();
		for (var entry : stock) {
			String id = String.valueOf(entry.get("id"));
			String itemId = String.valueOf(entry.get("item"));
			int count = entry.get("count") instanceof Number n ? Math.max(1, n.intValue()) : 1;
			String currency = String.valueOf(entry.get("currency"));
			long price = entry.get("price") instanceof Number n ? Math.max(0, n.longValue()) : 0;
			boolean sellable = entry.get("sellable") instanceof Boolean b && b;
			double sellRate = entry.get("sellRate") instanceof Number n ? n.doubleValue() : 0.5;
			int stockCount = entry.get("stock") instanceof Number n ? n.intValue() : -1;
			Long minPrice = entry.get("minPrice") instanceof Number n ? n.longValue() : null;
			Long maxPrice = entry.get("maxPrice") instanceof Number n ? n.longValue() : null;
			int fluctuationIntervalTicks = entry.get("fluctuationIntervalTicks") instanceof Number n ? Math.max(20, n.intValue()) : 1200;
			parsed.add(new KubeUIShopStock(id, itemId, count, currency, price, sellable, sellRate, minPrice, maxPrice, fluctuationIntervalTicks, stockCount));
		}
		KubeUIShop.define(shopId, displayName, parsed);
	}

	// ---------------------------------------------------------------- dialogue

	/// Declares a dialogue graph (re-declared every server boot, same convention as [#defineQuest]) -
	/// `rootNodeId` is where [KubeUI#dialogue] starts, `nodes` maps node id -> a plain JS object:
	/// ```js
	/// KubeUIActions.defineDialogue('old_sage', 'greeting', {
	///   greeting: {
	///     text: 'Welcome, traveler.', portrait: 'minecraft:villager', sound: 'minecraft:entity.villager.ambient',
	///     choices: [
	///       { label: 'Tell me about the ruins.', next: 'ruins' },
	///       { label: '[Requires 10 gold] A trade?', next: 'trade', requires: { currency: 'gold', amount: 10 } },
	///       { label: 'Goodbye.', next: null },
	///     ],
	///   },
	///   ruins: { text: 'Long ago...', choices: [ { label: 'Back', next: 'greeting' } ] },
	///   trade: { text: 'A pleasure.', timerSeconds: 10, timeoutNext: 'greeting', choices: [ ... ] },
	/// });
	/// ```
	/// `requires` (optional, per choice) gates that choice on the *player's* real server state -
	/// `{quest: id}`/`{quest: id, state: 'completed'}` ([KubeUIQuests#state], default state checked
	/// is `'active'`), `{currency: id, amount: n}` ([KubeUICurrency#balance]), or `{item: id, count:
	/// n}` (real inventory count) - a choice that fails its `requires` is simply omitted from what
	/// the server sends back, never shown disabled (there's no client-side concept of "this choice
	/// exists but is locked" to preserve here, unlike a quest log entry). `next: null` (or omitted)
	/// ends the dialogue. `text`/a choice's `label` can each optionally be `{key, fallback}` instead
	/// of a plain string - resolved the exact same way `.labelKey(...)` already resolves one
	/// (reusing that existing mechanism client-side rather than building a second one).
	public static void defineDialogue(String dialogueId, String rootNodeId, Map<String, Object> nodes) {
		KubeUIDialogue.define(dialogueId, rootNodeId, nodes);
	}

	/// Marks `entity` as starting `dialogueId` when right-clicked - works on any entity,
	/// same reasoning as [#tagTradePool]/[#tagQuestGiver]. Not the *only* way to start a dialogue:
	/// any other server-side event a script already listens to can open the exact same dialogue for
	/// a specific player via [#openRemote]-style delivery - `KubeUI.dialogue(dialogueId)` client-side
	/// (no npc argument) does that from a script's own trigger, this is just the built-in "right-click
	/// an NPC" trigger so scripts don't have to hand-roll interaction handling for the common case.
	public static void tagDialogueNpc(Entity entity, String dialogueId) {
		KubeUIDialogue.tagNpc(entity, dialogueId);
	}

	/// Every dialogue node id `player` has ever actually reached (via [KubeUIDialogue#resolveNode]),
	/// oldest first, deduplicated in place (reaching the same node twice doesn't add a second entry) -
	/// A persisted choice history, backed by the player's own persistent data like
	/// [#playerData], so an NPC can "remember" a past conversation across reconnects/restarts.
	public static List<String> dialogueHistory(ServerPlayer player, String dialogueId) {
		return KubeUIDialogue.history(player, dialogueId);
	}

	// ---------------------------------------------------------------- progression & skills

	/// Declares a skill tree (re-declared every server boot, same convention as [#defineDialogue]) -
	/// `nodes` maps node id -> a plain JS object:
	/// ```js
	/// KubeUIActions.defineSkillTree('combat', {
	///   toughness1: { name: 'Toughness I', cost: 1, icon: 'minecraft:golden_apple', effects: [
	///     { attribute: 'minecraft:max_health', amount: 4, operation: 'add_value' },
	///   ] },
	///   toughness2: { name: 'Toughness II', cost: 2, requires: ['toughness1'], icon: 'minecraft:totem_of_undying', effects: [
	///     { attribute: 'minecraft:max_health', amount: 4, operation: 'add_value' },
	///   ] },
	///   berserker: { name: 'Berserker', cost: 1, classGroup: 'berserker', icon: 'kubeui:textures/gui/skills/berserker.png', effects: [
	///     { attribute: 'minecraft:attack_damage', amount: 0.2, operation: 'add_multiplied_total' },
	///   ] },
	/// }, { respecCurrency: 'gold', respecCostMultiplier: 5, pointsPerLevel: 1 });
	/// ```
	/// `operation` is one of `'add_value'`/`'add_multiplied_base'`/`'add_multiplied_total'` (real
	/// vanilla `AttributeModifier.Operation` names). `icon` (optional) is shown next to the node on
	/// `KubeUI.skillTree(...)` - a real registered item id shows that item's own icon, anything else
	/// is used directly as a texture path (a resource pack/datapack image, own mod or KubeJS's own
	/// `kubejs/assets/...`) - one field covers both, resolved client-side, no separate flag needed.
	/// `classGroup` (optional) - the *first* `classGroup` node a player unlocks in this
	/// tree commits them to that group; any node from a *different* non-empty group is refused until
	/// a respec, letting a script build mutually-exclusive specializations without hand-rolled
	/// bookkeeping. `respecCurrency` (optional) + `respecCostMultiplier` (default `0`) price
	/// [#respecSkillTree] at `respecCostMultiplier * (number of unlocked nodes)`; omit
	/// `respecCurrency` for a free respec. `pointsPerLevel` (optional, default `0`) auto-grants that
	/// many points per real vanilla XP level gained (the "XP/level" source) - quest-triggered
	/// points (the third source) need no dedicated hook, a quest's own reward can already
	/// call [#grantSkillPoints].
	public static void defineSkillTree(String treeId, Map<String, Object> nodes, Map<String, Object> options) {
		double respecCostMultiplier = options.get("respecCostMultiplier") instanceof Number n ? n.doubleValue() : 0;
		String respecCurrency = options.get("respecCurrency") instanceof String s ? s : "";
		int pointsPerLevel = options.get("pointsPerLevel") instanceof Number n ? Math.max(0, n.intValue()) : 0;
		KubeUISkills.define(treeId, nodes, respecCostMultiplier, respecCurrency, pointsPerLevel);
	}

	public static int skillPoints(ServerPlayer player, String treeId) {
		return KubeUISkills.points(player, treeId);
	}

	public static void grantSkillPoints(ServerPlayer player, String treeId, int amount) {
		KubeUISkills.grantPoints(player, treeId, amount);
	}

	/// Atomic - fails cleanly if the node's prerequisites aren't met, it conflicts with
	/// an already-committed `classGroup`, or the player can't afford it. Applies the
	/// node's real `AttributeModifier`s on success.
	public static boolean unlockSkillNode(ServerPlayer player, String treeId, String nodeId) {
		return KubeUISkills.unlockNode(player, treeId, nodeId);
	}

	/// Refunds every point spent in `treeId` and removes every applied effect - see
	/// [KubeUISkills#respec] for the real cost rule.
	public static boolean respecSkillTree(ServerPlayer player, String treeId) {
		return KubeUISkills.respec(player, treeId);
	}

	// ---------------------------------------------------------------- machines & automation

	/// Declares a machine kind (re-declared every server boot, same convention as
	/// [#defineSkillTree]) - `redstoneMode` is `"ignore"` (default, always ticks), `"requireSignal"`
	/// (only ticks while powered) or `"disableOnSignal"`. A single input/output pair
	/// per kind, deliberately simpler than a multi-input recipe - see [KubeUIMachineBlockEntity]'s
	/// own class doc for why. Give a player a real placeable block for this kind with
	/// [#giveMachineItem].
	public static void defineMachine(String kind, String displayName, String inputItem, int inputCount, String outputItem, int outputCount, int processTicks, int energyPerTick, String redstoneMode) {
		KubeUIMachines.defineKind(kind, displayName, inputItem, inputCount, outputItem, outputCount, processTicks, energyPerTick, redstoneMode);
	}

	/// Registers `itemId` as a machine upgrade - placed in a machine's upgrade slot,
	/// multiplies its processing speed and adds `yieldBonus` extra output items per craft.
	public static void registerMachineUpgrade(String itemId, double speedMultiplier, double yieldBonus) {
		KubeUIMachines.registerUpgrade(itemId, speedMultiplier, yieldBonus);
	}

	/// Gives `player` a real, placeable `kubeui:machine` item already set to `kind` - placing it
	/// creates a block entity that's genuinely that kind from the moment it's placed (see
	/// [KubeUIMachineBlock#setPlacedBy]). There's only ever one real registered block/item behind
	/// every kind (NeoForge registries are frozen long before a `server_scripts` reload could ever
	/// define a new one, so a genuinely separate `Block` per script-defined kind isn't something a
	/// script can do) - what makes a "Crusher" and a "Smelter" actually read as two different things
	/// is the item's own display name, stamped here from `kind`'s real registered
	/// [KubeUIMachines.Kind#displayName] so it already shows correctly in the inventory/hotbar
	/// tooltip, before it's even placed - not just after opening the placed block's own screen.
	public static void giveMachineItem(ServerPlayer player, String kind) {
		var stack = new net.minecraft.world.item.ItemStack(KubeUIBlocks.MACHINE.get());
		var tag = new CompoundTag();
		tag.putString("kind", kind);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));

		var def = KubeUIMachines.kind(kind);
		if (def != null) {
			stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(def.displayName()));
		} else {
			KubeUI.LOGGER.warn("KubeUI: giveMachineItem(player, \"{}\") called, but no script has called defineMachine(\"{}\", ...) yet - "
				+ "the given item will show as a generic, kind-less machine (status \"No recipe\") until one does. "
				+ "Make sure defineMachine(...) runs (top-level server_scripts code, not inside a button/action callback) before giveMachineItem(...) is ever called for that kind.", kind, kind);
		}

		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/// Feeds `amount` energy into the machine at `pos`, capped at its own `maxEnergy` - the real,
	/// generic way to power a machine kind defined with `energyPerTick > 0` (see [#defineMachine]).
	/// A genuine energy-mod bridge should call this from its own capability-provider callback
	/// instead of a manual script action, but nothing here needs to know the difference. Returns
	/// how much was actually accepted (less than `amount` once the machine is near `maxEnergy`), or
	/// `0` if there's no real machine block entity at `pos`.
	public static int chargeMachine(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, int amount) {
		if (level.getBlockEntity(pos) instanceof KubeUIMachineBlockEntity machine) {
			return machine.receiveEnergy(amount, false);
		}
		return 0;
	}

	/// Draws a purely logical link (no physical pipe involved) from the machine at
	/// `from` to the one at `to` - after every successful craft, `from` tries pushing its output
	/// straight into `to`'s input slot. Both positions need a real [KubeUIMachineBlockEntity]
	/// already there, or this silently does nothing.
	public static void linkMachineOutput(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos from, net.minecraft.core.BlockPos to) {
		if (level.getBlockEntity(from) instanceof KubeUIMachineBlockEntity machine) {
			machine.setOutputLink(to);
		}
	}

	/// Links the machine at `pos` into network `networkId` - see
	/// `KubeUI.machineNetworkStatus(networkId)` for the aggregated controller view.
	public static void setMachineNetwork(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, String networkId) {
		if (level.getBlockEntity(pos) instanceof KubeUIMachineBlockEntity machine) {
			machine.setNetworkId(networkId);
		}
	}

	// ---------------------------------------------------------------- combat: boss bar

	/// Shows a custom boss bar to `viewers` - `phases` is a list of plain JS objects
	/// `{threshold, color, text}` (health fraction `0..1`, ARGB color, phase text) - the tightest
	/// matching phase (smallest `threshold` still `>=` current health fraction) drives the bar's
	/// color/subtitle, so a script can list phases in any order. See [KubeUIBossBar]'s own doc for
	/// why this is fully custom-drawn rather than built on vanilla `ServerBossEvent`.
	public static void showBossBar(List<ServerPlayer> viewers, String barId, String name, float health, float maxHealth, List<Map<String, Object>> phases) {
		KubeUIBossBar.show(viewers, barId, name, health, maxHealth, parsePhases(phases));
	}

	/// Updates a boss bar already shown via [#showBossBar] - `phases` from the original call are
	/// remembered, only `health` needs to be re-sent.
	public static void updateBossBarHealth(List<ServerPlayer> viewers, String barId, String name, float health, float maxHealth) {
		KubeUIBossBar.updateHealth(viewers, barId, name, health, maxHealth);
	}

	public static void hideBossBar(List<ServerPlayer> viewers, String barId) {
		KubeUIBossBar.hide(viewers, barId);
	}

	private static List<KubeUIBossBar.Phase> parsePhases(List<Map<String, Object>> phases) {
		var parsed = new ArrayList<KubeUIBossBar.Phase>();
		for (var entry : phases) {
			float threshold = entry.get("threshold") instanceof Number n ? n.floatValue() : 1.0F;
			int color = entry.get("color") instanceof Number n ? n.intValue() : 0xFFCC3333;
			String text = entry.get("text") instanceof String s ? s : "";
			parsed.add(new KubeUIBossBar.Phase(threshold, color, text));
		}
		return parsed;
	}

	// ---------------------------------------------------------------- combat: custom statuses

	/// Declares a purely scriptable status (re-declared every server boot, same convention as
	/// [#defineMachine]) - `icon` resolves exactly like a skill node's own `icon` field (a real
	/// registered item id shows that item's icon, anything else is used as a texture path). See
	/// [KubeUIStatusEffects]'s own doc for why a status here has no built-in game logic at all -
	/// that's entirely `KubeUICombatEvents.statusTick(event => {...})`'s job.
	public static void defineStatus(String id, String name, String icon) {
		KubeUIStatusEffects.define(id, name, icon);
	}

	/// Applies (or refreshes) `statusId` on `player` for `durationTicks` (`-1` for infinite, cleared
	/// only by [#removeStatus]) - no-ops if `statusId` was never declared via [#defineStatus].
	public static void applyStatus(ServerPlayer player, String statusId, int durationTicks) {
		KubeUIStatusEffects.apply(player, statusId, durationTicks);
	}

	public static void removeStatus(ServerPlayer player, String statusId) {
		KubeUIStatusEffects.remove(player, statusId);
	}

	public static boolean hasStatus(ServerPlayer player, String statusId) {
		return KubeUIStatusEffects.has(player, statusId);
	}

	// ---------------------------------------------------------------- combat: cooldowns

	/// Starts (or restarts) a purely scriptable, HUD-visible cooldown - independent of real vanilla
	/// `Player#getCooldowns()` (item-keyed only), see [KubeUICooldowns]'s own doc for why.
	public static void startCooldown(ServerPlayer player, String id, int durationTicks) {
		KubeUICooldowns.start(player, id, durationTicks);
	}

	public static boolean isCooldownActive(ServerPlayer player, String id) {
		return KubeUICooldowns.isActive(player, id);
	}

	public static int cooldownRemaining(ServerPlayer player, String id) {
		return KubeUICooldowns.remaining(player, id);
	}

	public static void clearCooldown(ServerPlayer player, String id) {
		KubeUICooldowns.clear(player, id);
	}

	// ---------------------------------------------------------------- combat: log & history

	/// Turns `player`'s combat-log HUD (recent hits dealt/taken) on or off - the "activable/
	/// désactivable par joueur" ask, see [KubeUICombatLog].
	public static void setCombatLogEnabled(ServerPlayer player, boolean enabled) {
		KubeUICombatLog.setEnabled(player, enabled);
	}

	/// Most recent first - every tracked combat session for `player` this session (in-memory only,
	/// same honest "not disk-persisted" scope [#tradeHistory]/[#dialogueHistory] already accept),
	/// each entry a real `CompoundTag` with `damageDealt`/`damageTaken`/`durationTicks`/`victory`/
	/// `timestamp` fields.
	public static List<CompoundTag> combatHistory(ServerPlayer player) {
		var result = new ArrayList<CompoundTag>();
		for (var entry : KubeUICombatLog.history(player)) {
			var tag = new CompoundTag();
			tag.putFloat("damageDealt", entry.damageDealt());
			tag.putFloat("damageTaken", entry.damageTaken());
			tag.putLong("durationTicks", entry.durationTicks());
			tag.putBoolean("victory", entry.victory());
			tag.putLong("timestamp", entry.timestamp());
			result.add(tag);
		}
		return result;
	}

	// ---------------------------------------------------------------- combat: AOE indicator

	public static void showAoeIndicator(ServerPlayer player, float radiusBlocks, int color) {
		KubeUIAoeIndicator.show(player, radiusBlocks, color);
	}

	public static void hideAoeIndicator(ServerPlayer player) {
		KubeUIAoeIndicator.hide(player);
	}

	// ---------------------------------------------------------------- structures & loot tables

	/// A real loot table's *probable* (never guaranteed) contents, sampled [KubeUILootPreview]
	/// times - see its own doc for why sampling, not exact odds. `lootTableId` is a real registered
	/// loot table id (e.g. `"minecraft:chests/simple_dungeon"`), not a KubeUI-defined one (see
	/// [#defineLootTable] for that).
	public static Map<String, Integer> lootTableBrowser(ServerPlayer player, String lootTableId) {
		return KubeUILootPreview.sample(player, lootTableId);
	}

	/// Declares a structure info zone (re-declared every server boot) - a plain bounding box, not a
	/// real vanilla generated `Structure` lookup, see [KubeUIStructureZones]'s own doc for why.
	public static void defineStructure(String id, String name, String difficulty, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		KubeUIStructureZones.define(id, name, difficulty, x1, y1, z1, x2, y2, z2, dimension);
	}

	/// Opts `player` in/out of the structure-approach toast popup - on by default.
	public static void setStructureInfoEnabled(ServerPlayer player, boolean enabled) {
		KubeUIStructureZones.setEnabled(player, enabled);
	}

	// ---------------------------------------------------------------- dungeons

	/// Declares a dungeon (re-declared every server boot) - `roomIds` in the order a player must
	/// visit them; see [KubeUIDungeons]'s own doc for why progress is per-player, not per-group.
	public static void defineDungeon(String id, String name, List<String> roomIds) {
		KubeUIDungeons.define(id, name, roomIds);
	}

	public static boolean isDungeonRoomUnlocked(String dungeonId, ServerPlayer player, String roomId) {
		return KubeUIDungeons.isRoomUnlocked(dungeonId, player, roomId);
	}

	/// Marks `roomId` visited for `player` - fails cleanly (returns `false`, no mutation) if that
	/// room isn't actually unlocked yet (its predecessor room hasn't been visited).
	public static boolean markDungeonRoomVisited(String dungeonId, ServerPlayer player, String roomId) {
		return KubeUIDungeons.markRoomVisited(dungeonId, player, roomId);
	}

	public static void markDungeonChestOpened(String dungeonId, ServerPlayer player, String chestId) {
		KubeUIDungeons.markChestOpened(dungeonId, player, chestId);
	}

	/// Records the dungeon as cleared for `player` and files a leaderboard entry - fails cleanly
	/// (returns `false`) if any room is still unvisited, so a script can't credit a skipped run.
	public static boolean markDungeonBossDefeated(String dungeonId, ServerPlayer player) {
		return KubeUIDungeons.markBossDefeated(dungeonId, player);
	}

	/// A real `CompoundTag` (`name`, `rooms` - each with `id`/`visited`/`unlocked`, `chestsOpened`,
	/// `bossDefeated`) for `player`'s current progress in `dungeonId` - build a checklist screen
	/// from it directly, the same "server replies, script draws" shape every other bridge screen in
	/// this mod already uses.
	public static CompoundTag dungeonProgress(String dungeonId, ServerPlayer player) {
		return KubeUIDungeons.progress(dungeonId, player);
	}

	/// Fastest completions first, each entry a real `CompoundTag` with `playerName`/`timeMs`.
	public static List<CompoundTag> dungeonLeaderboard(String dungeonId, int limit) {
		var result = new ArrayList<CompoundTag>();
		for (var entry : KubeUIDungeons.leaderboard(dungeonId, limit)) {
			var tag = new CompoundTag();
			tag.putString("playerName", entry.playerName());
			tag.putLong("timeMs", entry.timeMs());
			result.add(tag);
		}
		return result;
	}

	/// Clears every player's tracked progress for `dungeonId` - a script is expected to gate this
	/// behind its own `KubeUI.confirm()` and warn whichever players it considers "the group
	/// concerned", see [KubeUIDungeons]'s own doc for why that's left entirely up to the script.
	public static void resetDungeon(String dungeonId) {
		KubeUIDungeons.reset(dungeonId);
	}

	// ---------------------------------------------------------------- scriptable loot

	/// Declares a weighted loot table beyond real vanilla `LootTable`s (re-declared every server
	/// boot) - `entries` is a list of plain JS objects `{item, count, weight}`. See
	/// [KubeUICustomLoot]'s own doc for why this exists alongside real loot tables rather than
	/// replacing them.
	public static void defineLootTable(String tableId, List<Map<String, Object>> entries) {
		var parsed = new ArrayList<KubeUICustomLoot.Entry>();
		for (var entry : entries) {
			String itemId = String.valueOf(entry.get("item"));
			int count = entry.get("count") instanceof Number n ? Math.max(1, n.intValue()) : 1;
			double weight = entry.get("weight") instanceof Number n ? Math.max(0.0001, n.doubleValue()) : 1.0;
			parsed.add(new KubeUICustomLoot.Entry(itemId, count, weight));
		}
		KubeUICustomLoot.define(tableId, parsed);
	}

	/// Rolls `tableId` once, real weighted-random - `difficultyMultiplier` (default `1.0`) skews the
	/// odds toward low-weight ("rare") entries as it goes above `1.0`, see [KubeUICustomLoot]'s own
	/// doc for the exact formula. Returns an empty stack if `tableId` isn't defined or rolled empty.
	public static net.minecraft.world.item.ItemStack rollLoot(String tableId, double difficultyMultiplier) {
		return KubeUICustomLoot.roll(tableId, difficultyMultiplier);
	}

	// ---------------------------------------------------------------- group loot voting

	/// Starts a need/greed vote among `participants` for `count` of `itemId` - returns a `voteId`
	/// a script hands to whatever screen it builds for each participant to actually cast a choice
	/// with (see [KubeUILootVote]'s own doc for why the screen itself isn't built here).
	public static String startLootVote(String itemId, int count, List<ServerPlayer> participants) {
		return KubeUILootVote.start(itemId, count, participants);
	}

	/// Casts `player`'s choice (`"need"`/`"greed"`/`"pass"`) - once every participant has voted,
	/// resolves the vote and gives the item to the winner, returning their name (`""` if the vote
	/// isn't resolved yet, or nobody won).
	public static String castLootVote(String voteId, ServerPlayer player, String choice) {
		return KubeUILootVote.castVote(voteId, player, choice);
	}

	// ---------------------------------------------------------------- guilds & factions

	/// Creates a guild owned by `founder` - fails (`false`) if `guildId` is already taken or
	/// `founder` already belongs to a guild (one guild per player at a time, see [KubeUIGuilds]'s
	/// own doc for the fixed owner/officer/member role hierarchy).
	public static boolean createGuild(ServerPlayer founder, String guildId, String name) {
		return KubeUIGuilds.create(founder, guildId, name);
	}

	/// `""` if `player` isn't in a guild.
	public static String guildOf(ServerPlayer player) {
		var guild = KubeUIGuilds.guildOf(player);
		return guild != null ? guild.id : "";
	}

	/// `player`'s guild's real display name (`""` if they're not in one) - the tag a script (or
	/// [dev.kubeui.plugin.KubeUIChatEvents]'s own global-chat decoration) shows next to their name,
	/// as opposed to [#guildOf]'s own `guildId`.
	public static String guildNameOf(ServerPlayer player) {
		var guild = KubeUIGuilds.guildOf(player);
		return guild != null ? guild.name : "";
	}

	/// Only the guild's owner can rename it - "donner un nom à notre guild via des commandes" (real
	/// ask: a guild's display name isn't fixed forever at creation).
	public static boolean renameGuild(ServerPlayer actor, String guildId, String newName) {
		return KubeUIGuilds.rename(actor, guildId, newName);
	}

	public static boolean inviteToGuild(ServerPlayer inviter, String guildId, ServerPlayer target) {
		return KubeUIGuilds.invite(inviter, guildId, target);
	}

	/// Redefines the guild role hierarchy, lowest-to-highest (default `["member", "officer",
	/// "owner"]`) - a script can add roles (e.g. a "co-owner" between officer and owner) or rename
	/// them entirely. New members always join at `roles[0]`; a guild's founder/only-ever-one-holder
	/// role is always `roles[roles.size() - 1]`. Existing members keep whatever role string they
	/// already had even if it's no longer in the new list (not retroactively bumped/demoted) -
	/// "vraiment tout en JS" (real ask): the whole role model is a script's call, not fixed in Java.
	public static void setGuildRoles(List<String> roles) {
		KubeUIGuilds.setRoles(roles);
	}

	public static List<String> guildRoles() {
		return KubeUIGuilds.roles();
	}

	/// Sets the minimum role [#guildRoles] needs to perform `action` (`"invite"`/`"kick"`/
	/// `"setRole"` - default `"officer"`/`"officer"`/`"owner"` respectively). `minRole` must already
	/// be one of [#guildRoles].
	public static void setGuildActionRequirement(String action, String minRole) {
		KubeUIGuilds.setActionRequirement(action, minRole);
	}

	/// Sets the guild leveling curve (default `maxLevel=20`, `xpPerLevel=500`) - level is always
	/// `min(maxLevel, xp / xpPerLevel)`.
	public static void setGuildLevelCurve(int maxLevel, long xpPerLevel) {
		KubeUIGuilds.setLevelCurve(maxLevel, xpPerLevel);
	}

	public static boolean kickFromGuild(ServerPlayer actor, String guildId, UUID targetId) {
		return KubeUIGuilds.kick(actor, guildId, targetId);
	}

	/// `role` is `"officer"` or `"member"` (an owner can't be demoted this way - a guild always has
	/// exactly one, see [KubeUIGuilds]).
	public static boolean setGuildRole(ServerPlayer actor, String guildId, UUID targetId, String role) {
		return KubeUIGuilds.setRole(actor, guildId, targetId, role);
	}

	/// Every member of `guildId`, each a real `CompoundTag` with `id`/`role`.
	public static List<CompoundTag> guildMembers(String guildId) {
		var guild = KubeUIGuilds.get(guildId);
		var result = new ArrayList<CompoundTag>();
		if (guild == null) {
			return result;
		}
		for (var member : guild.members.entrySet()) {
			var tag = new CompoundTag();
			tag.putString("id", member.getKey().toString());
			tag.putString("role", member.getValue());
			result.add(tag);
		}
		return result;
	}

	/// Sends `message` to every currently-online member of `sender`'s guild, prefixed with the
	/// guild's name - no-ops if `sender` isn't in a guild.
	public static void sendGuildChat(ServerPlayer sender, String message) {
		KubeUIGuilds.sendChat(sender, message);
	}

	/// Bulk-authorizes every current member of `guildId` on the real storage crate at `pos` (see
	/// [#storageAddAuthorized] for the single-player equivalent) - reuses the exact same real
	/// per-player authorization list a crate already has, just seeded from guild membership instead
	/// of one player at a time. Re-run this after a new member joins to extend access to them too -
	/// membership changes don't retroactively update crates on their own.
	public static void authorizeGuildOnStorage(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, String guildId) {
		var guild = KubeUIGuilds.get(guildId);
		if (guild == null || !(level.getBlockEntity(pos) instanceof KubeUIStorageBlockEntity storage)) {
			return;
		}
		for (var memberId : guild.members.keySet()) {
			storage.addAuthorizedPlayer(memberId);
		}
	}

	public static void setGuildRelation(String guildA, String guildB, String relation) {
		KubeUIGuilds.setRelation(guildA, guildB, relation);
	}

	/// `"ally"`/`"rival"`/`"neutral"` (the default, if never set).
	public static String guildRelation(String guildA, String guildB) {
		return KubeUIGuilds.relation(guildA, guildB);
	}

	public static void addGuildXp(String guildId, long amount) {
		KubeUIGuilds.addXp(guildId, amount);
	}

	public static int guildLevel(String guildId) {
		return KubeUIGuilds.level(guildId);
	}

	/// Every guild, highest XP first, each a real `CompoundTag` with `id`/`name`/`xp`/`level`.
	public static List<CompoundTag> guildLeaderboard(int limit) {
		var result = new ArrayList<CompoundTag>();
		for (var guild : KubeUIGuilds.byXpDescending()) {
			if (result.size() >= limit) {
				break;
			}
			var tag = new CompoundTag();
			tag.putString("id", guild.id);
			tag.putString("name", guild.name);
			tag.putLong("xp", guild.xp);
			tag.putInt("level", KubeUIGuilds.level(guild.id));
			result.add(tag);
		}
		return result;
	}

	// ---------------------------------------------------------------- custom leaderboards ("DB" access)

	/// Sets `player`'s score on `boardId` (any script-chosen id - a new board is created the first
	/// time it's used) - real, disk-persisted, server-authoritative storage a script can build any
	/// leaderboard on top of ("je veux pouvoir communiqué en JS avec les DB" - a general-purpose
	/// score store, not just the already-specific [#guildLeaderboard]/[#dungeonLeaderboard]).
	public static void setLeaderboardScore(String boardId, ServerPlayer player, double score) {
		KubeUILeaderboards.setScore(boardId, player.getUUID(), player.getGameProfile().name(), score);
	}

	public static double leaderboardScoreOf(String boardId, ServerPlayer player) {
		return KubeUILeaderboards.scoreOf(boardId, player.getUUID());
	}

	public static boolean removeLeaderboardScore(String boardId, ServerPlayer player) {
		return KubeUILeaderboards.removeScore(boardId, player.getUUID());
	}

	/// Deletes `boardId` entirely (every player's score on it).
	public static void clearLeaderboard(String boardId) {
		KubeUILeaderboards.clearBoard(boardId);
	}

	/// Highest score first, each a real `CompoundTag` with `playerId`/`playerName`/`score`.
	public static List<CompoundTag> leaderboardTop(String boardId, int limit) {
		var result = new ArrayList<CompoundTag>();
		for (var entry : KubeUILeaderboards.top(boardId, limit)) {
			var tag = new CompoundTag();
			tag.putString("playerId", entry.playerId().toString());
			tag.putString("playerName", entry.playerName());
			tag.putDouble("score", entry.score());
			result.add(tag);
		}
		return result;
	}

	/// Every `boardId` that currently has at least one score recorded on it.
	public static List<String> leaderboardIds() {
		return KubeUILeaderboards.boardIds();
	}

	/// Sets `guildId`'s badge - `icon` resolves exactly like a skill node's own `icon` field (a
	/// real registered item id, or a texture path). See [KubeUIGuilds]'s own doc for why this
	/// doesn't attempt a real above-head/tab-list nameplate overlay - a script reads this back
	/// (`KubeUI.guildOf`-adjacent lookups a management screen can call) and draws it with the
	/// already-delivered `.badge()`/`.colorPicker()` widgets itself.
	public static void setGuildBadge(String guildId, int color, String icon) {
		KubeUIGuilds.setBadge(guildId, color, icon);
	}

	/// Declares/updates a scriptable structure zone claimed by `guildId` - a plain bounding box,
	/// see [KubeUIGuildTerritory]'s own doc for why real anti-grief protection isn't attempted here.
	public static void claimGuildTerritory(String guildId, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		KubeUIGuildTerritory.claim(guildId, x1, y1, z1, x2, y2, z2, dimension);
	}

	/// Schedules a named guild event `delayTicks` from now - fires
	/// `KubeUIGuildScriptEvents.scheduledEventTriggered(event => {...})` once the timer elapses (a
	/// real `server_scripts` event, registered alongside [KubeUICombatEvents]/[KubeUIEconomyEvents]).
	public static void scheduleGuildEvent(ServerPlayer any, String guildId, String eventId, String name, long delayTicks) {
		var server = any.level().getServer();
		if (server != null) {
			KubeUIGuildScheduledEvents.schedule(server, guildId, eventId, name, delayTicks);
		}
	}

	// ---------------------------------------------------------------- friends & parties

	public static boolean addFriend(ServerPlayer player, UUID friendId) {
		return KubeUIFriends.add(player, friendId);
	}

	public static boolean removeFriend(ServerPlayer player, UUID friendId) {
		return KubeUIFriends.remove(player, friendId);
	}

	public static List<UUID> friendsList(ServerPlayer player) {
		return KubeUIFriends.friendsOf(player);
	}

	/// A temporary, session-only group (see [KubeUIParties]'s own doc for why it's never persisted) -
	/// fails if `leader` is already in a party.
	public static boolean createParty(ServerPlayer leader) {
		return KubeUIParties.create(leader);
	}

	/// Only the party leader can invite by default - fails if `inviter` isn't allowed to (see
	/// [#setPartyAnyoneCanInvite]), has no party, or `target` already has one (their own or someone
	/// else's) or the party's already at [#setPartyMaxSize].
	public static boolean inviteToParty(ServerPlayer inviter, ServerPlayer target) {
		return KubeUIParties.invite(inviter, target);
	}

	/// `true` lets any member invite, not just the leader (default `false`) - "vraiment tout en JS"
	/// (real ask), a script's own call rather than fixed in Java.
	public static void setPartyAnyoneCanInvite(boolean value) {
		KubeUIParties.setAnyoneCanInvite(value);
	}

	/// Caps how many members one party can have (default unlimited - pass `0` or below to remove
	/// the cap again).
	public static void setPartyMaxSize(int value) {
		KubeUIParties.setMaxSize(value);
	}

	/// Leaving as the leader (or as the last remaining member) disbands the whole party.
	public static void leaveParty(ServerPlayer player) {
		KubeUIParties.leave(player);
	}

	/// Empty if `player` isn't in a party.
	public static List<UUID> partyMembers(ServerPlayer player) {
		var party = KubeUIParties.partyOf(player);
		return party != null ? List.copyOf(party.members) : List.of();
	}

	/// Only the party leader's call actually takes effect.
	public static void setPartySharing(ServerPlayer player, boolean sharedXp, boolean sharedLoot) {
		KubeUIParties.setSharing(player, sharedXp, sharedLoot);
	}

	public static boolean partySharesXp(ServerPlayer player) {
		var party = KubeUIParties.partyOf(player);
		return party != null && party.sharedXp;
	}

	public static boolean partySharesLoot(ServerPlayer player) {
		var party = KubeUIParties.partyOf(player);
		return party != null && party.sharedLoot;
	}

	public static void sendPartyChat(ServerPlayer sender, String message) {
		var party = KubeUIParties.partyOf(sender);
		if (party == null) {
			return;
		}
		var server = sender.level().getServer();
		if (server == null) {
			return;
		}
		var line = net.minecraft.network.chat.Component.literal("§b[Party] §f" + sender.getGameProfile().name() + ": §7" + message);
		for (var memberId : party.members) {
			var online = server.getPlayerList().getPlayer(memberId);
			if (online != null) {
				online.sendSystemMessage(line);
			}
		}
	}

	/// Real proximity chat - every real player within `radius` blocks, in the same dimension,
	/// hears it (not a filtered vanilla chat message, a direct system message so it can carry its
	/// own "[Local]" styling).
	public static void sendLocalChat(ServerPlayer sender, String message, double radius) {
		var line = net.minecraft.network.chat.Component.literal("§7[Local] " + sender.getGameProfile().name() + ": " + message);
		for (var player : sender.level().players()) {
			if (player instanceof ServerPlayer other && other.distanceToSqr(sender) <= radius * radius) {
				other.sendSystemMessage(line);
			}
		}
	}

	/// A `/kubeui tpa`-like teleport request, needing real confirmation from `to` before anything
	/// moves - see [KubeUITeleportRequests]'s own doc.
	public static void requestTeleport(ServerPlayer from, ServerPlayer to) {
		KubeUITeleportRequests.request(from, to);
	}

	/// Teleports the requester to `target` if a real, still-pending request exists - returns the
	/// requester's name (`""` if there's no valid pending request to accept).
	public static String acceptTeleport(ServerPlayer target) {
		return KubeUITeleportRequests.accept(target);
	}

	public static void denyTeleport(ServerPlayer target) {
		KubeUITeleportRequests.deny(target);
	}

	/// `"online"` (default)/`"in_combat"` (real, computed from [KubeUICombatLog])/a script-set
	/// custom string (`#setPresence`) - see [KubeUIPresence]'s own doc.
	public static String presenceOf(ServerPlayer player) {
		return KubeUIPresence.of(player);
	}

	public static void setPresence(ServerPlayer player, String status) {
		KubeUIPresence.set(player, status);
	}

	public static void setFriendActivityNotifications(ServerPlayer player, boolean enabled) {
		KubeUISocialEvents.setNotificationsEnabled(player, enabled);
	}

	// ---------------------------------------------------------------- housing & claims

	/// Configures the shared claim limits (not per-player - a single modpack-wide policy, set once
	/// by whichever script calls this) - default 3 claims, 10 000 blocks each.
	public static void setClaimLimits(int maxClaimsPerPlayer, double maxClaimSizeBlocks) {
		KubeUIClaims.setLimits(maxClaimsPerPlayer, maxClaimSizeBlocks);
	}

	/// Claims a real bounding box for `owner` - fails cleanly (`false`) past the configured
	/// per-player claim count/size limits, or if `claimId` is already taken.
	public static boolean claimLand(ServerPlayer owner, String claimId, double x1, double y1, double z1, double x2, double y2, double z2, String dimension) {
		return KubeUIClaims.claim(owner, claimId, x1, y1, z1, x2, y2, z2, dimension);
	}

	/// Every claim `owner` owns - each a real `CompoundTag` with `id`/`x1`/`y1`/`z1`/`x2`/`y2`/`z2`/
	/// `dimension`/`sizeBlocks` - backs a real "/claims"-style listing command ("une commande pour
	/// voir les claims" - real ask).
	public static List<CompoundTag> claimsOf(ServerPlayer owner) {
		var result = new ArrayList<CompoundTag>();
		for (var claim : KubeUIClaims.ownedBy(owner.getUUID())) {
			var tag = new CompoundTag();
			tag.putString("id", claim.id);
			tag.putDouble("x1", claim.x1);
			tag.putDouble("y1", claim.y1);
			tag.putDouble("z1", claim.z1);
			tag.putDouble("x2", claim.x2);
			tag.putDouble("y2", claim.y2);
			tag.putDouble("z2", claim.z2);
			tag.putString("dimension", claim.dimension);
			tag.putDouble("sizeBlocks", claim.sizeBlocks());
			result.add(tag);
		}
		return result;
	}

	public static boolean addClaimMember(ServerPlayer owner, String claimId, UUID memberId) {
		return KubeUIClaims.addMember(owner, claimId, memberId);
	}

	public static boolean removeClaimMember(ServerPlayer owner, String claimId, UUID memberId) {
		return KubeUIClaims.removeMember(owner, claimId, memberId);
	}

	/// `action` is a free-form string a script defines the meaning of (`"break"`/`"place"`/
	/// `"interact"`, ...) - this class only stores the flag, a script checks
	/// [#claimPermissionAllowed] itself wherever that action would happen.
	public static boolean setClaimPermission(ServerPlayer owner, String claimId, String action, boolean allowed) {
		return KubeUIClaims.setPermission(owner, claimId, action, allowed);
	}

	public static boolean claimPermissionAllowed(String claimId, String action, boolean defaultValue) {
		return KubeUIClaims.permissionAllowed(claimId, action, defaultValue);
	}

	/// Grants `targetId` temporary, revocable access to `claimId` for `durationTicks` - a real
	/// expiring grant, not a permanent membership change.
	public static boolean inviteClaimVisit(ServerPlayer owner, String claimId, UUID targetId, long durationTicks) {
		return KubeUIClaims.inviteVisit(owner, claimId, targetId, durationTicks);
	}

	public static boolean revokeClaimVisit(ServerPlayer owner, String claimId, UUID targetId) {
		return KubeUIClaims.revokeVisit(owner, claimId, targetId);
	}

	/// Charges `renter` `price` `currency` up front (atomic, real [#charge]) then grants them
	/// member-shaped access to `claimId` for `durationTicks`, auto-expiring.
	public static boolean rentClaim(ServerPlayer owner, String claimId, ServerPlayer renter, String currency, long price, long durationTicks) {
		return KubeUIClaims.rent(owner, claimId, renter, currency, price, durationTicks);
	}

	/// Merges `claimId2` into `claimId1` as their real combined bounding box - see
	/// [KubeUIClaims#merge]'s own doc for why this isn't a strict adjacency-checked merge.
	public static boolean mergeClaims(ServerPlayer owner, String claimId1, String claimId2) {
		return KubeUIClaims.merge(owner, claimId1, claimId2);
	}

	/// Splits `claimId` along `"x"`/`"z"` at `coordinate` into `newId1` (lower half)/`newId2`
	/// (upper half).
	public static boolean splitClaim(ServerPlayer owner, String claimId, String axis, double coordinate, String newId1, String newId2) {
		return KubeUIClaims.split(owner, claimId, axis, coordinate, newId1, newId2);
	}

	/// Sets (or overwrites) a named home for `player` at their current position - a player can have
	/// several, unlike vanilla's single bed-based respawn point.
	public static void setHome(ServerPlayer player, String name) {
		KubeUIHomes.set(player, name);
	}

	public static boolean removeHome(ServerPlayer player, String name) {
		return KubeUIHomes.remove(player, name);
	}

	public static List<String> homeNames(ServerPlayer player) {
		return KubeUIHomes.names(player);
	}

	public static boolean teleportHome(ServerPlayer player, String name) {
		return KubeUIHomes.teleportTo(player, name);
	}

	// ---------------------------------------------------------------- server dashboard

	/// A real, OP-gated snapshot of every major KubeUI system currently active on this server -
	/// `""` (and no data) if `player` isn't a real operator (`Commands.LEVEL_GAMEMASTERS`, the same
	/// real permission check `/money deposit`/`.withdraw` already use). For an admin who wants a
	/// state-of-the-server overview without hunting through several separate commands.
	public static CompoundTag serverDashboard(ServerPlayer player) {
		var data = new CompoundTag();
		if (!player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) {
			return data;
		}
		data.putInt("currencies", KubeUICurrency.knownCurrencies().size());
		data.putInt("guilds", KubeUIGuilds.count());
		data.putInt("claims", KubeUIClaims.count());
		data.putInt("dungeons", KubeUIDungeons.count());
		data.putInt("quests", KubeUIQuests.all().size());
		data.putInt("onlinePlayers", player.level().getServer() != null ? player.level().getServer().getPlayerList().getPlayerCount() : 0);
		return data;
	}
}
