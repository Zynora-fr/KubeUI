// Server-authoritative backend for the shop's "validated" purchase button (see
// kubeui_test_shop.js). This is the real point of KubeUIActions: the client sends only an item
// id, never a price - the server looks up the real price itself (SHOP_PRICES below) and decides
// whether the purchase is allowed. Balances go through the real, disk-persisted KubeUICurrency
// "gold" ledger (KubeUIActions.pay/.charge/.balance) - the same virtual currency every other
// KubeUI shop uses, not a throwaway in-memory map that resets on server restart. Fund your
// balance for testing via /money deposit <player> <amount> gold (needs OP) or the Economy Demo's
// "Earn 20 gold" button.

KubeUIActions.registerCurrency('gold')

const SHOP_PRICES = {
    'minecraft:apple': 2,
    'minecraft:bread': 3,
    'minecraft:iron_ingot': 10,
    'minecraft:ender_pearl': 15,
    'minecraft:gold_ingot': 20,
    'minecraft:emerald': 30,
    'minecraft:diamond': 50,
    'minecraft:totem_of_undying': 100,
}

KubeUIActions.register('kubeui_shop:buy_validated', (player, data) => {
    let itemId = data.getStringOr('item', '')
    let price = SHOP_PRICES[itemId]

    if (!price) {
        player.tell('§cUnknown item: ' + itemId)
        return
    }

    if (!KubeUIActions.charge(player, 'gold', price)) {
        player.tell('§c[Server-validated] Not enough gold for ' + itemId + ' - you have ' + KubeUIActions.balance(player, 'gold') + ', need ' + price + '.')
        return
    }

    player.give(itemId)
    player.tell('§a[Server-validated] Bought ' + itemId + ' for ' + price + ' gold. You have ' + KubeUIActions.balance(player, 'gold') + ' left.')
})
