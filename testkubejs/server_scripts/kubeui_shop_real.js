// A shop that behaves like it would on a real survival server, not a demo: currency is the same
// virtual "gold" ledger every other KubeUI shop runs on (see kubeui_economy_demo.js /
// KubeUICurrency) - not a raw item pulled from the inventory, so it can't be duped by dropping/
// hoarding emeralds and persists through KubeUICurrency's own disk-backed ledger across restarts.
// Every check happens here in server_scripts, and a successful sale is logged to the server
// console the way a real shop plugin keeps an audit trail. The client (see
// client_scripts/kubeui_shop_real.js) only ever sends an item id - it has no way to lie about
// how much it's paying, because the server never reads a price or a balance from it.

KubeUIActions.registerCurrency('gold')

// The catalog lives here, not in the client script - this is the only copy that's ever trusted.
// The client keeps its own copy purely to render the list before you click Buy.
const SHOP_CATALOG = {
    'minecraft:apple': { name: 'Apple', price: 1 },
    'minecraft:bread': { name: 'Bread', price: 2 },
    'minecraft:arrow': { name: 'Arrows', price: 2, count: 16 },
    'minecraft:iron_ingot': { name: 'Iron Ingot', price: 3 },
    'minecraft:ender_pearl': { name: 'Ender Pearl', price: 4 },
    'minecraft:golden_apple': { name: 'Golden Apple', price: 6 },
    'minecraft:diamond': { name: 'Diamond', price: 9 },
    'minecraft:totem_of_undying': { name: 'Totem of Undying', price: 20 },
}

KubeUIActions.register('kubeui_shop:buy_real', (player, data) => {
    let itemId = data.getStringOr('item', '')
    let entry = SHOP_CATALOG[itemId]

    if (!entry) {
        player.tell('§cThe shop doesn\'t sell that.')
        console.warn('[kubeui shop] ' + player.username + ' tried to buy an unknown item: ' + itemId)
        return
    }

    let count = entry.count || 1
    let price = entry.price

    // Simulate the payout first: if there's no room for it, don't take the player's gold for
    // nothing. `insertItem(stack, true)` never mutates the inventory - it just reports back
    // whatever wouldn't fit.
    let leftover = player.inventory.insertItem(Item.of(itemId, count), true)
    if (leftover.getCount() > 0) {
        player.tell('§cYour inventory is full - make room before buying ' + entry.name + '.')
        return
    }

    // KubeUIActions.charge is atomic - fails cleanly (nothing taken) if the balance is too low,
    // no separate "count then remove" dance like the old raw-emerald version needed.
    if (!KubeUIActions.charge(player, 'gold', price)) {
        player.tell('§cNot enough gold for ' + entry.name + ' - you have ' + KubeUIActions.balance(player, 'gold') + ', need ' + price + '.')
        return
    }

    player.inventory.insertItem(Item.of(itemId, count), false)

    player.tell('§aBought ' + entry.name + ' for ' + price + ' gold. Balance: ' + KubeUIActions.balance(player, 'gold') + '.')
    console.log('[kubeui shop] ' + player.username + ' bought ' + count + 'x ' + itemId + ' for ' + price + ' gold')
})
