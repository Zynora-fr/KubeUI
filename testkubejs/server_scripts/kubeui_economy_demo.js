// Reference example for the economy system - a scriptable,
// server-authoritative currency ('gold') independent of any physical item, unlike the older
// kubeui_shop_real.js demo (which counts real emeralds by hand). Everything here goes through
// KubeUICurrency under the hood via KubeUIActions.pay/.charge/.transferCurrency/.exchange* -
// nothing client-trusted, same posture as every other KubeUIActions-backed system.

KubeUIActions.registerCurrency('gold')
KubeUIActions.setCurrencyTransferTax('gold', 0.1) // 10% commission on player-to-player transfers

KubeUIActions.defineShop('kubeui_demo:general_store', 'General Store', [
    { id: 'bread', item: 'minecraft:bread', count: 4, currency: 'gold', price: 5, sellable: true, sellRate: 0.4, stock: -1 },
    { id: 'iron', item: 'minecraft:iron_ingot', count: 1, currency: 'gold', price: 12, sellable: true, sellRate: 0.5, stock: 32, minPrice: 8, maxPrice: 20, fluctuationIntervalTicks: 600 },
    { id: 'diamond', item: 'minecraft:diamond', count: 1, currency: 'gold', price: 80, sellable: false, stock: 5, minPrice: 60, maxPrice: 120, fluctuationIntervalTicks: 1200 },
])

// A quest-style "grant an allowance" action, for the demo screen's "Earn 20 gold" button - a real
// server would gate this behind something (a job, a quest reward) rather than an unconditional
// button, but this is the same "demo grants it directly" shape kubeui_test_shop.js already uses.
KubeUIActions.register('kubeui_demo:economy_earn', (player, data) => {
    KubeUIActions.pay(player, 'gold', 20)
    player.tell('§a+20 gold. Balance: ' + KubeUIActions.balance(player, 'gold'))
})

KubeUIActions.register('kubeui_demo:economy_spend', (player, data) => {
    if (KubeUIActions.charge(player, 'gold', 15)) {
        player.tell('§e-15 gold. Balance: ' + KubeUIActions.balance(player, 'gold'))
    } else {
        player.tell('§cNot enough gold (have ' + KubeUIActions.balance(player, 'gold') + ', need 15).')
    }
})

// Trade 8 rotten flesh for 3 gold, or the reverse (spend 3 gold for 8 rotten flesh),
// both atomic and server-verified (fails cleanly if you don't actually have/afford it).
KubeUIActions.register('kubeui_demo:economy_sell_junk', (player, data) => {
    if (KubeUIActions.exchangeItemForCurrency(player, 'minecraft:rotten_flesh', 8, 'gold', 3)) {
        player.tell('§aTraded 8 rotten flesh for 3 gold.')
    } else {
        player.tell('§cYou need 8 rotten flesh to make that trade.')
    }
})

KubeUIActions.register('kubeui_demo:economy_buy_junk', (player, data) => {
    if (KubeUIActions.exchangeCurrencyForItem(player, 'gold', 3, 'minecraft:rotten_flesh', 8)) {
        player.tell('§aBought 8 rotten flesh for 3 gold.')
    } else {
        player.tell('§cNot enough gold.')
    }
})

KubeUIEconomyEvents.transaction(event => {
    console.log('[kubeui economy] ' + event.player.username + ' ' + event.type + ' ' + Math.abs(event.delta) + ' ' + event.currency + ' (new balance: ' + event.balance + ')')
})
