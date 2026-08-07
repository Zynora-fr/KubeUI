// Advanced reference example - real custom trading built entirely on KubeUI's own primitives
// (weighted trade pools, server-authoritative execution, real per-entity persisted stock) instead
// of integrating with vanilla's VillagerTrades/MerchantOffer system, which turned out to have been
// completely overhauled in this Minecraft version with no simple, safely-guessable shape to build
// on (see KubeUIVillagerTrades's class doc for the full reasoning).
//
// Try it: tag any entity as a trader with
//   /kubeui tag-trader @e[type=minecraft:villager,limit=1,sort=nearest] kubeui_demo:trader
// (not villager-specific - tag anything, including something you /summon yourself), then
// right-click it - KubeUI opens the trade screen automatically, no client script needed for that
// part at all.

KubeUIActions.registerTradePool('kubeui_demo:trader', [
    { id: 'wood_for_emerald', weight: 10, costs: [{ item: 'minecraft:oak_log', count: 5 }], resultItem: 'minecraft:emerald', resultCount: 1, maxUses: 3, restockTicks: 6000 },
    { id: 'iron_for_iron_block', weight: 8, costs: [{ item: 'minecraft:iron_ingot', count: 9 }], resultItem: 'minecraft:iron_block', resultCount: 1, maxUses: 5, restockTicks: 12000 },
    { id: 'emerald_for_diamond', weight: 3, costs: [{ item: 'minecraft:emerald', count: 10 }], resultItem: 'minecraft:diamond', resultCount: 1, maxUses: 1, restockTicks: 24000 },
])

// A second pool demonstrating condition-gated trades (Phase-equivalent "reputation/quest"): only
// shows up once the quest board example's "Gather Wood" quest has actually been completed - real
// cross-feature integration, not a fabricated example. Tag a *different* entity with this pool to
// try it: /kubeui tag-trader @e[type=minecraft:villager,limit=1,sort=nearest] kubeui_demo:quest_reward_trader
KubeUIActions.registerTradePool('kubeui_demo:quest_reward_trader', [
    { id: 'secret_deal', weight: 1, costs: [{ item: 'minecraft:gold_ingot', count: 1 }], resultItem: 'minecraft:netherite_scrap', resultCount: 1, maxUses: 1, restockTicks: 0 },
], (player, tradeId) => {
    let quests = KubeUIActions.playerData(player)
    return quests.getStringOr('quest_gather_wood', '') === 'completed'
})

KubeUIActions.register('kubeui_demo:trade_history', (player, data) => {
    let history = KubeUIActions.tradeHistory(player)
    player.tell('§bTrade history: ' + (history.length > 0 ? history.join(', ') : '(none yet)'))
})
