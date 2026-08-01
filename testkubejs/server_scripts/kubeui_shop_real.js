// A shop that behaves like it would on a real survival server, not a demo: currency is real
// emeralds pulled straight from the player's inventory (not a JS number that resets on relog),
// every check happens here in server_scripts, and a successful sale is logged to the server
// console the way a real shop plugin keeps an audit trail. The client (see
// client_scripts/kubeui_shop_real.js) only ever sends an item id - it has no way to lie about
// how much it's paying, because the server never reads a price or a balance from it.

const CURRENCY_ITEM = 'minecraft:emerald'

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

// Drains `amount` currency items out of the player's inventory. Only call this after confirming
// (via kjs$count, below) that the player actually has enough - a stack can be split across
// several slots, so this keeps pulling from matched slots until the full amount is gone.
//
// Uses .getCount() (an explicit method call) rather than the .count bean property - in-game
// testing showed .count comes back undefined on a stack read via getStackInSlot(), which turned
// into a silent NaN and crashed extractItem() ("Cannot convert NaN to int"). getCount() is
// ItemStack's real, unambiguous vanilla method (confirmed in the decompiled MC source), so it
// isn't subject to whatever Rhino bean-mapping quirk broke the shorthand.
function removeCurrency(player, amount) {
    let remaining = amount
    let inventory = player.inventory
    let safetyGuard = 0 // extra insurance against ever looping forever if a slot lookup misbehaves

    while (remaining > 0 && safetyGuard < 64) {
        safetyGuard++

        let slot = inventory.find(CURRENCY_ITEM)
        if (slot < 0) return false // shouldn't happen after the count() check above, but never trust it blindly

        let take = Math.min(remaining, inventory.getStackInSlot(slot).getCount())
        inventory.extractItem(slot, take, false)
        remaining -= take
    }

    return remaining <= 0
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
    let owned = player.inventory.count(CURRENCY_ITEM)

    if (owned < price) {
        player.tell('§cNot enough emeralds for ' + entry.name + ' - you have ' + owned + ', need ' + price + '.')
        return
    }

    // Simulate the payout first: if there's no room for it, don't take the player's emeralds for
    // nothing. `insertItem(stack, true)` never mutates the inventory - it just reports back
    // whatever wouldn't fit.
    let leftover = player.inventory.insertItem(Item.of(itemId, count), true)
    if (leftover.getCount() > 0) {
        player.tell('§cYour inventory is full - make room before buying ' + entry.name + '.')
        return
    }

    if (!removeCurrency(player, price)) {
        player.tell('§cSomething went wrong taking your emeralds - purchase cancelled, nothing was charged.')
        console.warn('[kubeui shop] removeCurrency failed for ' + player.username + ' buying ' + itemId)
        return
    }

    player.inventory.insertItem(Item.of(itemId, count), false)

    player.tell('§aBought ' + entry.name + ' for ' + price + ' emerald' + (price === 1 ? '' : 's') + '.')
    console.log('[kubeui shop] ' + player.username + ' bought ' + count + 'x ' + itemId + ' for ' + price + ' emeralds')
})
