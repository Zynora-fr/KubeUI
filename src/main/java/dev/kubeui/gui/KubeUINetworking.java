package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/// Registers every KubeUI packet type and dispatches received ones - client -> server
/// ([KubeUIActionPayload], `screen.runServerAction(...)`) and server -> client ([KubeUIRemotePayload],
/// `KubeUIActions.openRemote(...)`/`.broadcastUpdate(...)`; [KubeUIActionAckPayload], the optional
/// reply to a `runServerAction(..., onAck)` call).
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUINetworking {
	private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger(1);
	private static final Map<Integer, BiConsumer<KubeUIContext, Boolean>> PENDING_ACKS = new ConcurrentHashMap<>();

	private KubeUINetworking() {
	}

	@SubscribeEvent
	static void register(RegisterPayloadHandlersEvent event) {
		var registrar = event.registrar("1");
		registrar.playToServer(KubeUIActionPayload.TYPE, KubeUIActionPayload.STREAM_CODEC, KubeUINetworking::handleAction);
		registrar.playToClient(KubeUIRemotePayload.TYPE, KubeUIRemotePayload.STREAM_CODEC, KubeUINetworking::handleRemote);
		registrar.playToClient(KubeUIActionAckPayload.TYPE, KubeUIActionAckPayload.STREAM_CODEC, KubeUINetworking::handleAck);
	}

	/// A player who disconnects mid-flight shouldn't leave a stale "screen open" entry behind
	/// forever - see [KubeUIActions#getOpenScreenId].
	@SubscribeEvent
	static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		KubeUIActions.clearOpenScreen(event.getEntity().getUUID());
	}

	private static void handleAction(KubeUIActionPayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (!(ctx.player() instanceof ServerPlayer serverPlayer)) {
				return;
			}

			boolean success = processAction(serverPlayer, payload);

			if (payload.requestId() != 0) {
				ctx.reply(new KubeUIActionAckPayload(payload.requestId(), success));
			}
		});
	}

	/// Reserved-action interception (screen open/close tracking), then throttle, then schema, then
	/// the actual handler - each guarded so a script bug/a malformed payload/a spammy client can
	/// never take the server thread down with it. Returns whether the action actually ran.
	private static boolean processAction(ServerPlayer player, KubeUIActionPayload payload) {
		String action = payload.action();

		if (KubeUIActions.SCREEN_STATE_ACTION.equals(action)) {
			String screenId = payload.data().getStringOr("screenId", null);
			boolean open = payload.data().getBooleanOr("open", false);
			KubeUIActions.setOpenScreen(player, open ? screenId : null);
			return true;
		}

		if (KubeUIActions.PERMISSION_CHECK_ACTION.equals(action)) {
			handlePermissionCheck(player, payload.data());
			return true;
		}

		if (KubeUIActions.RECIPE_QUERY_TYPE_ACTION.equals(action)) {
			String recipeTypeId = payload.data().getStringOr("value", "");
			var results = KubeUIRecipeQuery.queryByType(player, recipeTypeId);
			var reply = new CompoundTag();
			reply.put("recipes", results);
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.RECIPE_RESULT_TYPE_SCREEN_ID, reply));
			return true;
		}

		if (KubeUIActions.RECIPE_QUERY_ITEM_ACTION.equals(action)) {
			String itemId = payload.data().getStringOr("value", "");
			var results = KubeUIRecipeQuery.queryByItem(player, itemId);
			var reply = new CompoundTag();
			reply.put("recipes", results);
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.RECIPE_RESULT_ITEM_SCREEN_ID, reply));
			return true;
		}

		if (KubeUIActions.RECIPE_DESIGNER_LIST_ACTION.equals(action)) {
			sendRecipeDesignerList(player);
			return true;
		}

		if (KubeUIActions.RECIPE_DESIGNER_OPEN_GRID_ACTION.equals(action)) {
			String kind = payload.data().getStringOr("kind", "shapeless");
			KubeUIRecipeDesignerGrid.open(player, kind);
			return true;
		}

		if (KubeUIActions.RECIPE_DESIGNER_DELETE_ACTION.equals(action)) {
			String name = payload.data().getStringOr("name", "");
			KubeUIRecipeDesigner.delete(name, player);
			sendRecipeDesignerList(player);
			return true;
		}

		if (KubeUIActions.RECIPE_DESIGNER_RELOAD_ACTION.equals(action)) {
			KubeUIRecipeDesigner.reload(player);
			return true;
		}

		if (KubeUIActions.TRADER_DESIGNER_LIST_ACTION.equals(action)) {
			sendTraderDesignerList(player);
			return true;
		}

		if (KubeUIActions.TRADER_DESIGNER_OPEN_GRID_ACTION.equals(action)) {
			KubeUITraderGridMenu.open(player);
			return true;
		}

		if (KubeUIActions.TRADER_DESIGNER_REMOVE_TRADE_ACTION.equals(action)) {
			int index = payload.data().getIntOr("index", -1);
			KubeUITraderDesigner.removeTrade(player, index);
			sendTraderDesignerList(player);
			return true;
		}

		if (KubeUIActions.TRADER_DESIGNER_SET_FLAGS_ACTION.equals(action)) {
			boolean hasAI = payload.data().getBooleanOr("hasAI", true);
			boolean canMove = payload.data().getBooleanOr("canMove", true);
			KubeUITraderDesigner.setFlags(player, hasAI, canMove);
			sendTraderDesignerList(player);
			return true;
		}

		if (KubeUIActions.TRADER_DESIGNER_GIVE_EGG_ACTION.equals(action)) {
			KubeUITraderDesigner.giveEgg(player);
			sendTraderDesignerList(player);
			return true;
		}

		if (KubeUIActions.QUEST_LOG_REQUEST_ACTION.equals(action)) {
			sendQuestLog(player);
			return true;
		}

		if (KubeUIActions.QUEST_TRACK_ACTION.equals(action)) {
			String questId = payload.data().getStringOr("questId", "");
			KubeUIQuests.setTrackedQuest(player, questId);
			if (!questId.isEmpty()) {
				KubeUIQuestEvents.pushHudUpdate(player);
			}
			return true;
		}

		if (KubeUIActions.QUEST_GIVER_ACCEPT_ACTION.equals(action)) {
			handleQuestGiverAction(player, payload.data(), true);
			return true;
		}

		if (KubeUIActions.QUEST_GIVER_COMPLETE_ACTION.equals(action)) {
			handleQuestGiverAction(player, payload.data(), false);
			return true;
		}

		if (KubeUIActions.QUEST_COMMAND_ACCEPT_ACTION.equals(action)) {
			handleQuestCommand(player, payload.data().getStringOr("questId", ""), true);
			return true;
		}

		if (KubeUIActions.QUEST_COMMAND_COMPLETE_ACTION.equals(action)) {
			handleQuestCommand(player, payload.data().getStringOr("questId", ""), false);
			return true;
		}

		if (KubeUIActions.QUEST_EDITOR_LIST_ACTION.equals(action)) {
			sendQuestEditorList(player);
			return true;
		}

		if (KubeUIActions.QUEST_EDITOR_SAVE_ACTION.equals(action)) {
			var def = KubeUIQuests.questFromTag(payload.data());
			KubeUIQuests.defineEditorQuest(player.level().getServer(), def);
			sendQuestEditorList(player);
			return true;
		}

		if (KubeUIActions.QUEST_EDITOR_DELETE_ACTION.equals(action)) {
			String questId = payload.data().getStringOr("questId", "");
			KubeUIQuests.deleteEditorQuest(player.level().getServer(), questId);
			sendQuestEditorList(player);
			return true;
		}

		if (KubeUIActions.CURRENCY_HISTORY_ACTION.equals(action)) {
			String currency = payload.data().getStringOr("currency", "");
			var reply = new CompoundTag();
			var entriesTag = new net.minecraft.nbt.ListTag();
			for (var entry : KubeUICurrency.history(player, currency, 100)) {
				entriesTag.add(entry);
			}
			reply.put("entries", entriesTag);
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.CURRENCY_HISTORY_RESULT_SCREEN_ID, reply));
			return true;
		}

		if (KubeUIActions.LEADERBOARD_ACTION.equals(action)) {
			String currency = payload.data().getStringOr("currency", "");
			var reply = new CompoundTag();
			reply.putString("currency", currency);
			reply.put("entries", KubeUICurrency.topBalances(currency, 20));
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.LEADERBOARD_RESULT_SCREEN_ID, reply));
			return true;
		}

		if (KubeUIActions.SHOP_OPEN_ACTION.equals(action)) {
			sendShop(player, payload.data().getStringOr("shopId", ""));
			return true;
		}

		if (KubeUIActions.SHOP_BUY_ACTION.equals(action)) {
			String shopId = payload.data().getStringOr("shopId", "");
			String stockId = payload.data().getStringOr("stockId", "");
			int qty = Math.max(1, payload.data().getIntOr("qty", 1));
			KubeUIShop.buy(player, shopId, stockId, qty);
			sendShop(player, shopId);
			return true;
		}

		if (KubeUIActions.SHOP_SELL_ACTION.equals(action)) {
			String shopId = payload.data().getStringOr("shopId", "");
			String stockId = payload.data().getStringOr("stockId", "");
			int qty = Math.max(1, payload.data().getIntOr("qty", 1));
			KubeUIShop.sell(player, shopId, stockId, qty);
			sendShop(player, shopId);
			return true;
		}

		if (KubeUIActions.DIALOGUE_OPEN_ACTION.equals(action)) {
			handleDialogueOpen(player, payload.data());
			return true;
		}

		if (KubeUIActions.DIALOGUE_CHOOSE_ACTION.equals(action)) {
			handleDialogueChoose(player, payload.data());
			return true;
		}

		if (KubeUIActions.STORAGE_SORT_ACTION.equals(action)) {
			var storage = KubeUIStorageSessions.currentStorage(player);
			if (storage != null) {
				storage.sortBy(payload.data().getStringOr("key", "name"));
			}
			return true;
		}

		if (KubeUIActions.STORAGE_SET_FILTER_ACTION.equals(action)) {
			var storage = KubeUIStorageSessions.currentStorage(player);
			if (storage != null && storage.canOpen(player)) {
				var items = new java.util.ArrayList<String>();
				for (var tag : payload.data().getListOrEmpty("items")) {
					tag.asString().ifPresent(items::add);
				}
				storage.setFilter(payload.data().getStringOr("mode", "none"), items);
			}
			return true;
		}

		if (KubeUIActions.STORAGE_TOGGLE_LOCK_ACTION.equals(action)) {
			var storage = KubeUIStorageSessions.currentStorage(player);
			if (storage != null && player.getUUID().equals(storage.owner())) {
				storage.setLocked(!storage.isKubeLocked());
			}
			return true;
		}

		if (KubeUIActions.STORAGE_ADD_AUTHORIZED_ACTION.equals(action)) {
			var storage = KubeUIStorageSessions.currentStorage(player);
			var target = player.level().getServer().getPlayerList().getPlayerByName(payload.data().getStringOr("playerName", ""));
			if (storage != null && target != null && player.getUUID().equals(storage.owner())) {
				storage.addAuthorizedPlayer(target.getUUID());
			}
			return true;
		}

		if (KubeUIActions.STORAGE_REMOVE_AUTHORIZED_ACTION.equals(action)) {
			var storage = KubeUIStorageSessions.currentStorage(player);
			var target = player.level().getServer().getPlayerList().getPlayerByName(payload.data().getStringOr("playerName", ""));
			if (storage != null && target != null && player.getUUID().equals(storage.owner())) {
				storage.removeAuthorizedPlayer(target.getUUID());
			}
			return true;
		}

		if (KubeUIActions.STORAGE_SET_NETWORK_ACTION.equals(action)) {
			var storage = KubeUIStorageSessions.currentStorage(player);
			if (storage != null && storage.canOpen(player)) {
				storage.setNetworkId(payload.data().getStringOr("networkId", ""));
			}
			return true;
		}

		if (KubeUIActions.STORAGE_HISTORY_REQUEST_ACTION.equals(action)) {
			sendStorageHistory(player);
			return true;
		}

		if (KubeUIActions.STORAGE_NETWORK_VIEW_ACTION.equals(action)) {
			sendStorageNetworkView(player, payload.data().getStringOr("networkId", ""));
			return true;
		}

		if (KubeUIActions.SKILL_TREE_REQUEST_ACTION.equals(action)) {
			sendSkillTree(player, payload.data().getStringOr("treeId", ""));
			return true;
		}

		if (KubeUIActions.SKILL_UNLOCK_NODE_ACTION.equals(action)) {
			String treeId = payload.data().getStringOr("treeId", "");
			KubeUISkills.unlockNode(player, treeId, payload.data().getStringOr("nodeId", ""));
			sendSkillTree(player, treeId);
			return true;
		}

		if (KubeUIActions.SKILL_RESPEC_ACTION.equals(action)) {
			String treeId = payload.data().getStringOr("treeId", "");
			KubeUISkills.respec(player, treeId);
			sendSkillTree(player, treeId);
			return true;
		}

		if (KubeUIActions.SKILL_LEADERBOARD_ACTION.equals(action)) {
			sendSkillLeaderboard(player, payload.data().getStringOr("treeId", ""));
			return true;
		}

		if (KubeUIActions.MACHINE_NETWORK_STATUS_ACTION.equals(action)) {
			sendMachineNetworkStatus(player, payload.data().getStringOr("networkId", ""));
			return true;
		}

		if (KubeUIActions.WAYPOINT_SHARE_ACTION.equals(action)) {
			var target = player.level().getServer().getPlayerList().getPlayerByName(payload.data().getStringOr("targetPlayerName", ""));
			if (target != null) {
				var reply = payload.data().copy();
				reply.putString("senderName", player.getGameProfile().name());
				PacketDistributor.sendToPlayer(target, new KubeUIRemotePayload(KubeUIActions.WAYPOINT_SHARE_RESULT_SCREEN_ID, reply));
			}
			return true;
		}

		var handler = KubeUIActions.get(action);
		if (handler == null) {
			KubeUI.LOGGER.warn("Received unknown KubeUI action '{}' from {}", action, player.getName().getString());
			return false;
		}

		if (!KubeUIActions.tryConsumeThrottle(player, action)) {
			KubeUI.LOGGER.debug("KubeUI action '{}' from {} dropped - throttled", action, player.getName().getString());
			return false;
		}

		String schemaError = KubeUIActions.validate(action, payload.data());
		if (schemaError != null) {
			KubeUI.LOGGER.warn("KubeUI action '{}' from {} rejected - {}", action, player.getName().getString(), schemaError);
			return false;
		}

		try {
			handler.handle(player, payload.data());
			return true;
		} catch (Exception ex) {
			KubeUI.LOGGER.error("KubeUI action '{}' handler threw an exception (player: {})", action, player.getName().getString(), ex);
			return false;
		}
	}

	/// Checks every gate name in `data.gates` (see [KubeUIScreen#requestMissingPermissions]) via
	/// [KubeUIPermissions#check] and replies with a `screenId -> boolean` map - reusing
	/// [KubeUIRemotePayload]/[KubeUIActions#PERMISSION_RESULT_SCREEN_ID] rather than a dedicated
	/// payload type for what's still fundamentally "server tells this one client something".
	private static void handlePermissionCheck(ServerPlayer player, CompoundTag data) {
		var results = new CompoundTag();
		for (var tag : data.getListOrEmpty("gates")) {
			tag.asString().ifPresent(gate -> results.putBoolean(gate, KubeUIPermissions.check(player, gate)));
		}
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.PERMISSION_RESULT_SCREEN_ID, results));
	}

	private static void sendRecipeDesignerList(ServerPlayer player) {
		var reply = new CompoundTag();
		reply.put("recipes", KubeUIRecipeDesigner.listToTag(KubeUIRecipeDesigner.list()));
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.RECIPE_DESIGNER_RESULT_SCREEN_ID, reply));
	}

	/// Package-visible (not `private`) so [KubeUITraderGridMenu] can push the up-to-date list back
	/// after capturing a trade - that's what actually makes the Trader Designer screen reappear
	/// with the new trade already in it instead of leaving the player at nothing.
	static void sendTraderDesignerList(ServerPlayer player) {
		var reply = new CompoundTag();
		var tradesTag = new net.minecraft.nbt.ListTag();
		for (var def : KubeUITraderDesigner.trades(player)) {
			tradesTag.add(KubeUIVillagerTrades.tradeToTag(def));
		}
		reply.put("trades", tradesTag);
		reply.putBoolean("hasAI", KubeUITraderDesigner.hasAI(player));
		reply.putBoolean("canMove", KubeUITraderDesigner.canMove(player));
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.TRADER_DESIGNER_RESULT_SCREEN_ID, reply));
	}

	private static void sendQuestLog(ServerPlayer player) {
		var reply = new CompoundTag();
		var questsTag = new net.minecraft.nbt.ListTag();
		for (var def : KubeUIQuests.all()) {
			questsTag.add(KubeUIQuestGiverInteraction.describeQuestForPlayer(player, def));
		}
		reply.put("quests", questsTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.QUEST_LOG_RESULT_SCREEN_ID, reply));
	}

	/// `data` carries `questId` and `giverUuid` (a quest-giver screen always knows which entity it
	/// was opened from, resolved back to a real [net.minecraft.world.entity.Entity] here the same
	/// way any other UUID-addressed entity lookup works) - `accept` picks which of
	/// [KubeUIQuests#accept]/[KubeUIQuests#complete] to call. If the giver entity is no longer
	/// resolvable (unloaded, despawned since the screen opened), the state change still happens -
	/// only the follow-up screen refresh is skipped, since there's nothing left to refresh from.
	private static void handleQuestGiverAction(ServerPlayer player, CompoundTag data, boolean accept) {
		String questId = data.getStringOr("questId", "");
		if (accept) {
			KubeUIQuests.accept(player, questId);
		} else {
			KubeUIQuests.complete(player, questId);
		}

		try {
			var giverUuid = java.util.UUID.fromString(data.getStringOr("giverUuid", ""));
			var giver = player.level().getEntity(giverUuid);
			if (giver != null) {
				KubeUIQuestGiverInteraction.sendGiverScreen(player, giver);
			}
		} catch (IllegalArgumentException ignored) {
			// Malformed/missing giverUuid - nothing to refresh.
		}
	}

	/// Backs `/quest accept <questId>`/`/quest complete <questId>` - same underlying
	/// [KubeUIQuests#accept]/[KubeUIQuests#complete] the quest-giver screen's buttons call, but
	/// with an explicit chat message either way, since (unlike that screen) there's nothing on
	/// screen that would otherwise show whether it actually worked.
	private static void handleQuestCommand(ServerPlayer player, String questId, boolean accept) {
		var def = KubeUIQuests.get(questId);
		if (def == null) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Unknown quest '" + questId + "'."));
			return;
		}

		if (accept) {
			String state = KubeUIQuests.state(player, questId);
			if (!state.isEmpty()) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"'" + def.title() + "' " + ("completed".equals(state) ? "is already completed." : "is already active.")
				));
				return;
			}
			if (!KubeUIQuests.prerequisitesMet(player, def)) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"'" + def.title() + "' is locked - complete its prerequisite quest(s) first."
				));
				return;
			}
			KubeUIQuests.accept(player, questId);
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Accepted quest: " + def.title()));
		} else {
			if (!"active".equals(KubeUIQuests.state(player, questId))) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"'" + def.title() + "' isn't currently active - nothing to turn in."
				));
				return;
			}
			if (!KubeUIQuests.allObjectivesComplete(player, def)) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
					"'" + def.title() + "' isn't finished yet - check /quest for what's left."
				));
				return;
			}
			KubeUIQuests.complete(player, questId);
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Completed quest: " + def.title() + " - rewards granted!"));
		}
	}

	private static void sendQuestEditorList(ServerPlayer player) {
		var reply = new CompoundTag();
		var questsTag = new net.minecraft.nbt.ListTag();
		for (var def : KubeUIQuests.editorQuests()) {
			questsTag.add(KubeUIQuests.questToTag(def));
		}
		reply.put("quests", questsTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.QUEST_EDITOR_RESULT_SCREEN_ID, reply));
	}

	/// Sent both on the initial `SHOP_OPEN_ACTION` and again after every buy/sell, the same
	/// "mutate then re-send" shape [KubeUITraderDesigner]'s actions already use, so a shop screen
	/// always reflects the real, just-changed price/stock rather than the client guessing.
	private static void sendShop(ServerPlayer player, String shopId) {
		if (!KubeUIShop.exists(shopId)) {
			return;
		}
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.SHOP_RESULT_SCREEN_ID, KubeUIShop.describe(shopId)));
	}

	/// `KubeUI.dialogue(dialogueId, npcUuid)` - `npcUuid` is optional (empty/blank for a dialogue
	/// with no associated entity, e.g. one a script opens from a non-interaction trigger).
	private static void handleDialogueOpen(ServerPlayer player, CompoundTag data) {
		String dialogueId = data.getStringOr("dialogueId", "");
		var def = KubeUIDialogue.get(dialogueId);
		if (def == null) {
			return;
		}

		var npc = resolveNpc(player, data.getStringOr("npcUuid", ""));
		var reply = KubeUIDialogue.resolveNode(player, dialogueId, def.rootNodeId(), npc);
		if (reply != null) {
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.DIALOGUE_RESULT_SCREEN_ID, reply));
		}
	}

	private static void handleDialogueChoose(ServerPlayer player, CompoundTag data) {
		String dialogueId = data.getStringOr("dialogueId", "");
		String nodeId = data.getStringOr("nodeId", "");
		int choiceIndex = data.getIntOr("choiceIndex", -1);
		boolean timedOut = data.getBooleanOr("timedOut", false);
		var npc = resolveNpc(player, data.getStringOr("npcUuid", ""));

		var reply = KubeUIDialogue.choose(player, dialogueId, nodeId, choiceIndex, timedOut, npc);
		if (reply != null) {
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.DIALOGUE_RESULT_SCREEN_ID, reply));
		}
	}

	private static void sendStorageHistory(ServerPlayer player) {
		var storage = KubeUIStorageSessions.currentStorage(player);
		if (storage == null) {
			return;
		}
		var reply = new CompoundTag();
		var entriesTag = new net.minecraft.nbt.ListTag();
		for (var entry : storage.history()) {
			var tag = new CompoundTag();
			tag.putString("type", entry.type());
			tag.putString("item", entry.item());
			tag.putInt("count", entry.count());
			tag.putString("playerName", entry.playerName());
			tag.putLong("time", entry.time());
			entriesTag.add(tag);
		}
		reply.put("entries", entriesTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.STORAGE_HISTORY_RESULT_SCREEN_ID, reply));
	}

	private static void sendStorageNetworkView(ServerPlayer player, String networkId) {
		var reply = new CompoundTag();
		reply.putString("networkId", networkId);

		if (networkId.isEmpty() || !KubeUIStorageNetwork.hasAccess(networkId, player.getUUID())) {
			reply.putBoolean("denied", true);
		} else {
			var itemsTag = new net.minecraft.nbt.ListTag();
			KubeUIStorageNetwork.aggregate(networkId).forEach((itemId, count) -> {
				var tag = new CompoundTag();
				tag.putString("item", itemId);
				tag.putInt("count", count);
				itemsTag.add(tag);
			});
			reply.put("items", itemsTag);
		}
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.STORAGE_NETWORK_RESULT_SCREEN_ID, reply));
	}

	/// Sent on the initial `SKILL_TREE_REQUEST_ACTION` and again after every unlock/respec - same
	/// "mutate then re-send" convention as the storage/shop screens above.
	private static void sendSkillTree(ServerPlayer player, String treeId) {
		var def = KubeUISkills.get(treeId);
		if (def == null) {
			return;
		}

		var reply = new CompoundTag();
		reply.putString("treeId", treeId);
		reply.putInt("points", KubeUISkills.points(player, treeId));
		reply.putString("class", KubeUISkills.chosenClass(player, treeId));

		var unlocked = KubeUISkills.unlockedNodes(player, treeId);
		var unlockedTag = new net.minecraft.nbt.ListTag();
		unlocked.forEach(id -> unlockedTag.add(net.minecraft.nbt.StringTag.valueOf(id)));
		reply.put("unlocked", unlockedTag);

		var nodesTag = new net.minecraft.nbt.ListTag();
		for (var node : def.nodes().values()) {
			var nodeTag = new CompoundTag();
			nodeTag.putString("id", node.id());
			nodeTag.putString("name", node.name());
			nodeTag.putInt("cost", node.cost());
			nodeTag.putString("icon", node.icon());
			var requiresTag = new net.minecraft.nbt.ListTag();
			node.requires().forEach(id -> requiresTag.add(net.minecraft.nbt.StringTag.valueOf(id)));
			nodeTag.put("requires", requiresTag);
			nodeTag.putBoolean("met", unlocked.containsAll(node.requires()));
			nodesTag.add(nodeTag);
		}
		reply.put("nodes", nodesTag);

		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.SKILL_TREE_RESULT_SCREEN_ID, reply));
	}

	/// Deliberately reduced scope versus [#sendLeaderboard]'s all-time, offline-inclusive
	/// currency leaderboard: ranks *currently online* players only, by lifetime points earned
	/// (spent + unspent) in `treeId`. A real cross-session version would need the same
	/// `UUID`-keyed, disk-persisted ledger [KubeUICurrency] already has - not duplicated here purely
	/// to keep this one genuinely simple rather than half-reinventing that file.
	private static void sendSkillLeaderboard(ServerPlayer player, String treeId) {
		var def = KubeUISkills.get(treeId);
		var reply = new CompoundTag();
		reply.putString("treeId", treeId);
		var entriesTag = new net.minecraft.nbt.ListTag();

		if (def != null) {
			var scored = new java.util.ArrayList<java.util.Map.Entry<String, Integer>>();
			for (var online : player.level().getServer().getPlayerList().getPlayers()) {
				int spent = 0;
				for (var nodeId : KubeUISkills.unlockedNodes(online, treeId)) {
					var node = def.nodes().get(nodeId);
					if (node != null) {
						spent += node.cost();
					}
				}
				int total = spent + KubeUISkills.points(online, treeId);
				if (total > 0) {
					scored.add(java.util.Map.entry(online.getGameProfile().name(), total));
				}
			}
			scored.sort((a, b) -> b.getValue() - a.getValue());
			for (var entry : scored) {
				var tag = new CompoundTag();
				tag.putString("name", entry.getKey());
				tag.putInt("points", entry.getValue());
				entriesTag.add(tag);
			}
		}
		reply.put("entries", entriesTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.SKILL_LEADERBOARD_RESULT_SCREEN_ID, reply));
	}

	private static void sendMachineNetworkStatus(ServerPlayer player, String networkId) {
		var reply = new CompoundTag();
		reply.putString("networkId", networkId);
		var entriesTag = new net.minecraft.nbt.ListTag();
		for (var status : KubeUIMachineNetwork.status(networkId)) {
			var tag = new CompoundTag();
			tag.putString("kind", status.kind());
			tag.putInt("progress", status.progress());
			tag.putInt("processTicks", status.processTicks());
			tag.putInt("energy", status.energy());
			tag.putInt("maxEnergy", status.maxEnergy());
			tag.putLong("crafted", status.crafted());
			entriesTag.add(tag);
		}
		reply.put("entries", entriesTag);
		PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.MACHINE_NETWORK_STATUS_RESULT_SCREEN_ID, reply));
	}

	private static net.minecraft.world.entity.Entity resolveNpc(ServerPlayer player, String uuidString) {
		if (uuidString.isEmpty()) {
			return null;
		}
		try {
			return player.level().getEntity(java.util.UUID.fromString(uuidString));
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	private static void handleRemote(KubeUIRemotePayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (KubeUIActions.PERMISSION_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIScreen.receivePermissionResults(payload.data());
			} else if (KubeUIActions.SIDEBAR_VISIBILITY_SCREEN_ID.equals(payload.screenId())) {
				String iconId = payload.data().getStringOr("iconId", null);
				boolean visible = payload.data().getBooleanOr("visible", true);
				KubeUISidebar.setServerVisible(iconId, visible);
			} else if (KubeUIActions.RECIPE_RESULT_TYPE_SCREEN_ID.equals(payload.screenId())) {
				KubeUIRecipeBridge.receiveTypeResult(payload.data().getListOrEmpty("recipes"));
			} else if (KubeUIActions.RECIPE_RESULT_ITEM_SCREEN_ID.equals(payload.screenId())) {
				KubeUIRecipeBridge.receiveItemResult(payload.data().getListOrEmpty("recipes"));
			} else if (KubeUIActions.RECIPE_DESIGNER_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIRecipeDesignerScreen.receiveList(payload.data().getListOrEmpty("recipes"));
			} else if (KubeUIActions.TRADER_DESIGNER_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUITraderDesignerScreen.receiveList(payload.data());
			} else if (KubeUIActions.QUEST_LOG_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestLogScreen.receiveList(payload.data().getListOrEmpty("quests"));
			} else if (KubeUIActions.QUEST_EDITOR_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestEditorScreen.receiveList(payload.data().getListOrEmpty("quests"));
			} else if (KubeUIActions.QUEST_GIVER_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestGiverScreen.receive(payload.data());
			} else if (KubeUIActions.QUEST_HUD_UPDATE_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestHud.receive(payload.data());
			} else if (KubeUIActions.CURRENCY_HISTORY_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIEconomyBridge.receiveHistory(payload.data().getListOrEmpty("entries"));
			} else if (KubeUIActions.LEADERBOARD_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIEconomyBridge.receiveLeaderboard(payload.data());
			} else if (KubeUIActions.SHOP_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIEconomyBridge.receiveShop(payload.data());
			} else if (KubeUIActions.DIALOGUE_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIDialogueBridge.receive(payload.data());
			} else if (KubeUIActions.STORAGE_HISTORY_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIStorageBridge.receiveHistory(payload.data().getListOrEmpty("entries"));
			} else if (KubeUIActions.STORAGE_NETWORK_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIStorageBridge.receiveNetworkView(payload.data());
			} else if (KubeUIActions.SKILL_TREE_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUISkillBridge.receiveTree(payload.data());
			} else if (KubeUIActions.SKILL_LEADERBOARD_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUISkillBridge.receiveLeaderboard(payload.data());
			} else if (KubeUIActions.WAYPOINT_SHARE_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIWaypointBridge.receiveShared(payload.data());
			} else if (KubeUIActions.MACHINE_ALERT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIMachineAlerts.receive(payload.data());
			} else if (KubeUIActions.MACHINE_NETWORK_STATUS_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIMachineBridge.receiveNetworkStatus(payload.data());
			} else if (KubeUIActions.BOSS_BAR_UPDATE_SCREEN_ID.equals(payload.screenId())) {
				KubeUIBossBarHud.receive(payload.data());
			} else if (KubeUIActions.STATUS_UPDATE_SCREEN_ID.equals(payload.screenId())) {
				KubeUIStatusHud.receive(payload.data());
			} else if (KubeUIActions.COOLDOWN_UPDATE_SCREEN_ID.equals(payload.screenId())) {
				KubeUICooldownHud.receive(payload.data());
			} else if (KubeUIActions.COMBAT_LOG_UPDATE_SCREEN_ID.equals(payload.screenId())) {
				KubeUICombatLogHud.receive(payload.data());
			} else if (KubeUIActions.AOE_INDICATOR_UPDATE_SCREEN_ID.equals(payload.screenId())) {
				KubeUIAoeIndicatorHud.receive(payload.data());
			} else {
				KubeUIRemoteScreens.receive(payload.screenId(), payload.data());
			}
		});
	}

	private static void handleAck(KubeUIActionAckPayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			var callback = PENDING_ACKS.remove(payload.requestId());
			if (callback != null) {
				try {
					callback.accept(null, payload.success());
				} catch (Exception ex) {
					KubeUI.LOGGER.error("KubeUI runServerAction onAck callback threw an exception", ex);
				}
			}
		});
	}

	static void sendAction(String action, CompoundTag data) {
		ClientPacketDistributor.sendToServer(new KubeUIActionPayload(action, data == null ? new CompoundTag() : data));
	}

	/// A server that never replies to a `runServerAction(..., onAck)` call (doesn't implement that
	/// action at all, or is simply broken) would otherwise leave its entry in [#PENDING_ACKS]
	/// forever - found during a security/robustness audit, the same unbounded-growth
	/// shape as the [KubeUIScreenBuilder#PERSISTED] issue already fixed elsewhere. Real replies
	/// arrive within moments under normal play, so this is generous headroom, not a real limit.
	private static final int MAX_PENDING_ACKS = 500;

	/// Same as [#sendAction(String, CompoundTag)], but `onAck` (screen, success) is called once the
	/// server replies with a [KubeUIActionAckPayload] for this specific call.
	static void sendAction(String action, CompoundTag data, BiConsumer<KubeUIContext, Boolean> onAck) {
		if (PENDING_ACKS.size() >= MAX_PENDING_ACKS) {
			KubeUI.LOGGER.warn(
				"KubeUI: {}+ runServerAction(..., onAck) calls are still awaiting a reply - the server may not be replying to '{}' at all. Dropping this onAck instead of growing forever.",
				MAX_PENDING_ACKS, action
			);
			ClientPacketDistributor.sendToServer(new KubeUIActionPayload(action, data == null ? new CompoundTag() : data));
			return;
		}

		int requestId = NEXT_REQUEST_ID.getAndIncrement();
		PENDING_ACKS.put(requestId, onAck);
		ClientPacketDistributor.sendToServer(new KubeUIActionPayload(action, data == null ? new CompoundTag() : data, requestId));
	}

	/// Sent automatically by `KubeUIScreen` (never by scripts) when a `.screenId(id)`-tagged screen
	/// opens/closes - see [KubeUIActions#SCREEN_STATE_ACTION].
	static void sendScreenState(String screenId, boolean open) {
		var data = new CompoundTag();
		data.putString("screenId", screenId);
		data.putBoolean("open", open);
		sendAction(KubeUIActions.SCREEN_STATE_ACTION, data);
	}
}
