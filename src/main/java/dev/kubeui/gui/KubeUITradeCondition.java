package dev.kubeui.gui;

import net.minecraft.server.level.ServerPlayer;

/// Optional per-pool gate for `KubeUIActions.registerTradePool(poolId, trades, condition)` - a
/// trade only shows up for a player if this returns true for it (e.g. "only after completing a
/// quest" or "only above some reputation"), checked server-side whenever trades are (re)rolled for
/// that player - never assumed client-side.
public interface KubeUITradeCondition {
	boolean test(ServerPlayer player, String tradeId);
}
