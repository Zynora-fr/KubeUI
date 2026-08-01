// Exploratory test: an item shop. Clickable items (tooltip + click sound), a scrollable list
// built from a plain JS array (the "quels items" the shop sells is entirely script-defined - see
// SHOP_ITEMS below), a reactive gold counter driven by .bind(), a real error dialog when you
// can't afford something, and actually giving you the item on a successful purchase.
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.

let gold = 35
let purchaseCount = 0

ClientEvents.tick(event => {
    // Slow gold regen, just so .bind() below has something changing on its own to show off.
    if (event.player.tickCount % 100 === 0) {
        gold += 5
    }
})

// The shop's catalog - add/remove/reprice items here, nowhere else.
const SHOP_ITEMS = [
    { id: 'minecraft:apple', name: 'Apple', price: 2 },
    { id: 'minecraft:bread', name: 'Bread', price: 3 },
    { id: 'minecraft:iron_ingot', name: 'Iron Ingot', price: 10 },
    { id: 'minecraft:ender_pearl', name: 'Ender Pearl', price: 15 },
    { id: 'minecraft:gold_ingot', name: 'Gold Ingot', price: 20 },
    { id: 'minecraft:emerald', name: 'Emerald', price: 30 },
    { id: 'minecraft:diamond', name: 'Diamond', price: 50 },
    { id: 'minecraft:totem_of_undying', name: 'Totem of Undying', price: 100 },
]

function buy(screen, item) {
    if (gold < item.price) {
        // A real error dialog, not just a status label - modal, on top of the shop screen.
        KubeUI.alert(
            'Cannot afford this',
            item.name + ' costs ' + item.price + ' gold.\nYou only have ' + gold + ' gold.',
            null
        )
        return
    }

    gold -= item.price
    purchaseCount++
    screen.setLabel('gold', 'Gold: ' + gold)
    screen.setLabel('status', 'Bought ' + item.name + '! (' + purchaseCount + ' total)')

    // Actually gives the item (via a /give command through the normal client->server pipeline -
    // requires cheats/op, same as typing the command yourself; see KubeUIContext#giveItem).
    screen.giveItem(item.id, 1)
}

function openShop() {
    let builder = KubeUI.builder('Item Shop', 'kubeui_test:shop') // persistKey - the toggle below remembers its state
        .animated()
        .label('gold', 'Gold: ' + gold)
        .bind(() => gold, (screen, value) => screen.setLabel('gold', 'Gold: ' + value))
        .divider()

    builder.scrollPanel(150, panel => {
        SHOP_ITEMS.forEach(item => {
            panel.row(row => {
                row.item(item.id, 1, screen => screen.setLabel('status', 'That\'s ' + item.name + ', ' + item.price + ' gold.'))
                    .tooltip(item.name + ' - ' + item.price + ' gold')
                    .sound('minecraft:item.armor.equip_generic')

                row.label('name_' + item.id, item.name).width(110)

                row.button('Buy (' + item.price + 'g)', screen => buy(screen, item))
                    .width(80)
                    .sound('minecraft:entity.experience_orb.pickup')
            })
        })
    })

    builder
        .divider()
        .label('status', 'Click an item icon to inspect it, or Buy to purchase.')
        .toggle('rememberMe', 'This toggle remembers its state (persistKey)', true, (screen, value) => {})
        .divider()
        // The "Buy" buttons above trust the client (gold is a JS variable, giveItem runs a
        // client-sent command) - fine for a single-player demo, not for real survival. This one
        // instead sends only the item id to the server; kubeui_actions.js looks up the *real*
        // price itself and decides, so a modified client couldn't just lie about having gold.
        .label('validatedTitle', '-- Server-validated purchase (see server_scripts) --')
        .dropdown('validatedItem', SHOP_ITEMS.map(item => item.id), SHOP_ITEMS[0].id, (screen, value) => {})
        .button('Buy (Server-Validated)', screen => {
            let itemId = screen.getDropdownValue('validatedItem')
            screen.runServerAction('kubeui_shop:buy_validated', { item: itemId })
        })
        .button('Close', screen => screen.close())
        .onClose(screen => console.log('Shop closed after ' + purchaseCount + ' purchase(s) this session.'))
        .open()
}
