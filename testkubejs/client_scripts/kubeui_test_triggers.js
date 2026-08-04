// Demonstrates the three ways a KubeUI screen can be opened outside of a button click:
//   - a real client command (KubeUI.registerCommand)
//   - right-clicking an item (ItemEvents.rightClicked, same idea as kubeui_shop_real.js's block)
//   - right-clicking a block (BlockEvents.rightClicked - already shown for real in
//     kubeui_shop_real.js, repeated here with the test menu so all three live in one place)
// All three just call .open() from client code - opening a screen has always been a purely
// client-side decision in KubeUI, so none of this needs a server round trip.

KubeUI.registerCommand('kubeuimenu', () => {
    openKubeUITestMenu()
})

ItemEvents.rightClicked('minecraft:compass', event => {
    openKubeUITestMenu()
})

// Diamond Block, like Emerald Block in kubeui_shop_real.js, has no vanilla right-click behavior
// of its own to fight with or cancel.
BlockEvents.rightClicked('minecraft:diamond_block', event => {
    openKubeUITestMenu()
})
