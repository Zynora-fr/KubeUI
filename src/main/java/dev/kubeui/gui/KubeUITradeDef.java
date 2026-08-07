package dev.kubeui.gui;

import java.util.List;

/// One trade in a `KubeUIActions.registerTradePool(poolId, trades, condition)` pool (or baked
/// onto a [KubeUITraderEggItem] by [KubeUITraderDesigner]) - `id` is unique *within its pool* (not
/// globally), used for uses-tracking. `costs` are matched/consumed from real, player-filled menu
/// slots by [KubeUITradeExecuteMenu] - see [KubeUIVillagerTrades].
record KubeUITradeDef(
	String id,
	int weight,
	List<KubeUITradeCost> costs,
	String resultItem,
	int resultCount,
	int maxUses,
	int restockTicks
) {
}
