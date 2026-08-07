package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/// Server-side per-player "in-progress custom trader" session for `/kubeui trader-designer` - a
/// player builds up a list of trades (via [KubeUITraderGridMenu], a real item-slot GUI, not typed
/// ingredient ids) plus AI/movement flags, then "Give Egg" bakes it all onto a real
/// [KubeUITraderEggItem] stack instead of registering a script-side pool or tagging an existing
/// entity - the egg *is* the trader definition, droppable/giftable/storable like any item, and
/// spawns a real villager with these exact trades the moment it's used.
///
/// Session state is purely in-memory (keyed by player UUID, not persisted to disk) - unlike
/// [KubeUIRecipeDesigner]'s saved recipes, an in-progress trader has no reason to survive a
/// server restart; once "Give Egg" is pressed, the egg item itself is the only thing that needs to
/// persist, and items already persist themselves.
final class KubeUITraderDesigner {
	private KubeUITraderDesigner() {
	}

	private static final class Session {
		final List<KubeUITradeDef> trades = new ArrayList<>();
		boolean hasAI = true;
		boolean canMove = true;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private static Session session(ServerPlayer player) {
		return SESSIONS.computeIfAbsent(player.getUUID(), id -> new Session());
	}

	static List<KubeUITradeDef> trades(ServerPlayer player) {
		return List.copyOf(session(player).trades);
	}

	static void addTrade(ServerPlayer player, KubeUITradeDef def) {
		session(player).trades.add(def);
	}

	static void removeTrade(ServerPlayer player, int index) {
		var trades = session(player).trades;
		if (index >= 0 && index < trades.size()) {
			trades.remove(index);
		}
	}

	static boolean hasAI(ServerPlayer player) {
		return session(player).hasAI;
	}

	static boolean canMove(ServerPlayer player) {
		return session(player).canMove;
	}

	static void setFlags(ServerPlayer player, boolean hasAI, boolean canMove) {
		var session = session(player);
		session.hasAI = hasAI;
		session.canMove = canMove;
	}

	/// Bakes the session's trades/flags onto a new [KubeUITraderEggItem] stack, gives it to
	/// `player` (drops it at their feet if their inventory is full, same as a normal item pickup
	/// overflow), then clears the session so the next "Add Trade" starts a fresh trader. Refuses if
	/// there are no trades yet - an egg with nothing to trade would just be a silent dud.
	static boolean giveEgg(ServerPlayer player) {
		var session = session(player);
		if (session.trades.isEmpty()) {
			player.sendSystemMessage(Component.literal("Add at least one trade before giving the egg."));
			return false;
		}

		var tag = new CompoundTag();
		var tradesTag = new ListTag();
		for (var def : session.trades) {
			tradesTag.add(KubeUIVillagerTrades.tradeToTag(def));
		}
		tag.put("trades", tradesTag);
		tag.putBoolean("hasAI", session.hasAI);
		tag.putBoolean("canMove", session.canMove);

		var stack = new ItemStack(KubeUIItems.TRADER_EGG.get());
		CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag);
		stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
			Component.literal(session.trades.size() + " trade(s)"),
			Component.literal("AI: " + (session.hasAI ? "on" : "off") + ", Movement: " + (session.canMove ? "on" : "off"))
		)));

		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}

		SESSIONS.remove(player.getUUID());
		player.sendSystemMessage(Component.literal("Custom trader egg given - " + session.trades.size() + " trade(s) baked in. Right-click a block to spawn it."));
		return true;
	}
}
