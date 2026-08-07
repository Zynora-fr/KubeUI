package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

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
			String screenId = KubeUINbtCompat.getStringOr(payload.data(), "screenId", null);
			boolean open = KubeUINbtCompat.getBooleanOr(payload.data(), "open", false);
			KubeUIActions.setOpenScreen(player, open ? screenId : null);
			return true;
		}

		if (KubeUIActions.PERMISSION_CHECK_ACTION.equals(action)) {
			handlePermissionCheck(player, payload.data());
			return true;
		}

		if (KubeUIActions.RECIPE_QUERY_TYPE_ACTION.equals(action)) {
			String recipeTypeId = KubeUINbtCompat.getStringOr(payload.data(), "value", "");
			var results = KubeUIRecipeQuery.queryByType(player, recipeTypeId);
			var reply = new CompoundTag();
			reply.put("recipes", results);
			PacketDistributor.sendToPlayer(player, new KubeUIRemotePayload(KubeUIActions.RECIPE_RESULT_TYPE_SCREEN_ID, reply));
			return true;
		}

		if (KubeUIActions.RECIPE_QUERY_ITEM_ACTION.equals(action)) {
			String itemId = KubeUINbtCompat.getStringOr(payload.data(), "value", "");
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
			String kind = KubeUINbtCompat.getStringOr(payload.data(), "kind", "shapeless");
			KubeUIRecipeDesignerGrid.open(player, kind);
			return true;
		}

		if (KubeUIActions.RECIPE_DESIGNER_DELETE_ACTION.equals(action)) {
			String name = KubeUINbtCompat.getStringOr(payload.data(), "name", "");
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
			int index = KubeUINbtCompat.getIntOr(payload.data(), "index", -1);
			KubeUITraderDesigner.removeTrade(player, index);
			sendTraderDesignerList(player);
			return true;
		}

		if (KubeUIActions.TRADER_DESIGNER_SET_FLAGS_ACTION.equals(action)) {
			boolean hasAI = KubeUINbtCompat.getBooleanOr(payload.data(), "hasAI", true);
			boolean canMove = KubeUINbtCompat.getBooleanOr(payload.data(), "canMove", true);
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
			String questId = KubeUINbtCompat.getStringOr(payload.data(), "questId", "");
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
			handleQuestCommand(player, KubeUINbtCompat.getStringOr(payload.data(), "questId", ""), true);
			return true;
		}

		if (KubeUIActions.QUEST_COMMAND_COMPLETE_ACTION.equals(action)) {
			handleQuestCommand(player, KubeUINbtCompat.getStringOr(payload.data(), "questId", ""), false);
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
			String questId = KubeUINbtCompat.getStringOr(payload.data(), "questId", "");
			KubeUIQuests.deleteEditorQuest(player.level().getServer(), questId);
			sendQuestEditorList(player);
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
		for (var tag : data.getList("gates", 8)) {
			results.putBoolean(tag.getAsString(), KubeUIPermissions.check(player, tag.getAsString()));
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
		String questId = KubeUINbtCompat.getStringOr(data, "questId", "");
		if (accept) {
			KubeUIQuests.accept(player, questId);
		} else {
			KubeUIQuests.complete(player, questId);
		}

		try {
			var giverUuid = java.util.UUID.fromString(KubeUINbtCompat.getStringOr(data, "giverUuid", ""));
			var giver = player.serverLevel().getEntity(giverUuid);
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

	private static void handleRemote(KubeUIRemotePayload payload, IPayloadContext ctx) {
		ctx.enqueueWork(() -> {
			if (KubeUIActions.PERMISSION_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIScreen.receivePermissionResults(payload.data());
			} else if (KubeUIActions.SIDEBAR_VISIBILITY_SCREEN_ID.equals(payload.screenId())) {
				String iconId = KubeUINbtCompat.getStringOr(payload.data(), "iconId", null);
				boolean visible = KubeUINbtCompat.getBooleanOr(payload.data(), "visible", true);
				KubeUISidebar.setServerVisible(iconId, visible);
			} else if (KubeUIActions.RECIPE_RESULT_TYPE_SCREEN_ID.equals(payload.screenId())) {
				KubeUIRecipeBridge.receiveTypeResult(payload.data().getList("recipes", 10));
			} else if (KubeUIActions.RECIPE_RESULT_ITEM_SCREEN_ID.equals(payload.screenId())) {
				KubeUIRecipeBridge.receiveItemResult(payload.data().getList("recipes", 10));
			} else if (KubeUIActions.RECIPE_DESIGNER_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIRecipeDesignerScreen.receiveList(payload.data().getList("recipes", 10));
			} else if (KubeUIActions.TRADER_DESIGNER_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUITraderDesignerScreen.receiveList(payload.data());
			} else if (KubeUIActions.QUEST_LOG_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestLogScreen.receiveList(payload.data().getList("quests", 10));
			} else if (KubeUIActions.QUEST_EDITOR_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestEditorScreen.receiveList(payload.data().getList("quests", 10));
			} else if (KubeUIActions.QUEST_GIVER_RESULT_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestGiverScreen.receive(payload.data());
			} else if (KubeUIActions.QUEST_HUD_UPDATE_SCREEN_ID.equals(payload.screenId())) {
				KubeUIQuestHud.receive(payload.data());
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
		PacketDistributor.sendToServer(new KubeUIActionPayload(action, data == null ? new CompoundTag() : data));
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
			PacketDistributor.sendToServer(new KubeUIActionPayload(action, data == null ? new CompoundTag() : data));
			return;
		}

		int requestId = NEXT_REQUEST_ID.getAndIncrement();
		PENDING_ACKS.put(requestId, onAck);
		PacketDistributor.sendToServer(new KubeUIActionPayload(action, data == null ? new CompoundTag() : data, requestId));
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
