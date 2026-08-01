// Server-authoritative backend for the shop's "validated" purchase button (see
// kubeui_test_shop.js). This is the real point of KubeUIActions: the client sends only an item
// id, never a price - the server looks up the real price itself (SHOP_PRICES below) and decides
// whether the purchase is allowed. A client sending a fake/edited price wouldn't matter, because
// the server never reads one.

const SERVER_GOLD = {} // playerId -> gold. In-memory for this demo; a real shop would persist this.
const STARTING_GOLD = 35

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

function getGold(player) {
    let id = player.uuid
    if (!(id in SERVER_GOLD)) {
        SERVER_GOLD[id] = STARTING_GOLD
    }
    return SERVER_GOLD[id]
}

KubeUIActions.register('kubeui_shop:buy_validated', (player, data) => {
    let itemId = data.getStringOr('item', '')
    let price = SHOP_PRICES[itemId]

    if (!price) {
        player.tell('§cUnknown item: ' + itemId)
        return
    }

    let gold = getGold(player)

    if (gold < price) {
        player.tell('§c[Server-validated] Not enough gold for ' + itemId + ' - you have ' + gold + ', need ' + price + '.')
        return
    }

    SERVER_GOLD[player.uuid] = gold - price
    player.give(itemId)
    player.tell('§a[Server-validated] Bought ' + itemId + ' for ' + price + ' gold. You have ' + (gold - price) + ' left.')
})
