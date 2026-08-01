// The "real" shop: right-click an Emerald Block to open it, like a shop terminal a server admin
// would actually place down - not an auto-opened menu button. All it does client-side is draw
// the catalog and forward a plain item id to the server; the real logic (price, whether you can
// afford it, whether you have room, actually taking the emeralds and handing over the item) all
// lives in server_scripts/kubeui_shop_real.js. This mirrors what a real shop plugin looks like:
// the client is just a menu, the server is the cash register.

// Emerald Block has no vanilla right-click behavior of its own, so there's nothing to fight with
// or cancel here.
BlockEvents.rightClicked(event => {
    if (event.block.id !== 'minecraft:emerald_block') return
    openRealShop()
})

// Mirrors the server's catalog purely so the GUI has something to render (name, price, stack
// size) before you click Buy. This copy is never trusted - see the comment in
// server_scripts/kubeui_shop_real.js. If you add an item here without adding it there too, the
// button will just show "The shop doesn't sell that." instead of giving anything away for free.
const SHOP_DISPLAY = [
    { id: 'minecraft:apple', name: 'Apple', price: 1, count: 1 },
    { id: 'minecraft:bread', name: 'Bread', price: 2, count: 1 },
    { id: 'minecraft:arrow', name: 'Arrows (x16)', price: 2, count: 16 },
    { id: 'minecraft:iron_ingot', name: 'Iron Ingot', price: 3, count: 1 },
    { id: 'minecraft:ender_pearl', name: 'Ender Pearl', price: 4, count: 1 },
    { id: 'minecraft:golden_apple', name: 'Golden Apple', price: 6, count: 1 },
    { id: 'minecraft:diamond', name: 'Diamond', price: 9, count: 1 },
    { id: 'minecraft:totem_of_undying', name: 'Totem of Undying', price: 20, count: 1 },
]

function openRealShop() {
    let builder = KubeUI.builder('Emerald Shop')
        .label('info', 'Pay with real emeralds from your inventory.')
        .divider()

    builder.scrollPanel(150, panel => {
        SHOP_DISPLAY.forEach(item => {
            panel.row(row => {
                row.item(item.id, item.count)
                    .tooltip(item.name + ' - ' + item.price + ' emerald' + (item.price === 1 ? '' : 's'))
                    .sound('minecraft:item.armor.equip_generic')

                row.label('name_' + item.id, item.name).width(120)

                row.button('Buy (' + item.price + ' emerald' + (item.price === 1 ? '' : 's') + ')', screen => {
                    // Only ever sends the id. The server decides the price, whether it's
                    // affordable, and whether there's room - see kubeui_shop_real.js.
                    screen.runServerAction('kubeui_shop:buy_real', { item: item.id })
                })
                    .width(130)
                    .sound('minecraft:entity.experience_orb.pickup')
            })
        })
    })

    builder
        .divider()
        .label('note', 'Prices and stock are decided by the server - this screen is just a menu.')
        .button('Close', screen => screen.close())
        .open()
}
