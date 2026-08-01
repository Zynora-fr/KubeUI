// Paladium-style icon bar next to the survival inventory (press E). Each icon is either a
// vanilla item (KubeUISidebar.addItem) or a custom texture (KubeUISidebar.addTexture) and runs a
// plain callback when clicked - typically opening a KubeUI screen, same as any button. Registered
// once at script load; the icons then show up automatically every time the inventory screen opens,
// no per-open code needed.

KubeUISidebar.addItem('kubeui:widgets', 'minecraft:crafting_table', 'Widget Gallery', () => {
    openKubeUITestScreen()
})

KubeUISidebar.addItem('kubeui:ux', 'minecraft:writable_book', 'UX Demo', () => {
    openKubeUIUXDemo()
})

KubeUISidebar.addItem('kubeui:shop', 'minecraft:emerald', 'Emerald Shop', () => {
    openRealShop()
})

KubeUISidebar.addItem('kubeui:responsive', 'minecraft:spyglass', 'Responsive/Scale Demo', () => {
    openResponsiveDemo()
})

// Uncomment to try a custom-texture icon instead of an item (needs a real texture at that path
// under assets/kubeui/textures/... in a resource pack):
// KubeUISidebar.addTexture('kubeui:custom', 'kubeui:textures/gui/sidebar_icon.png', 'Custom Icon', () => {
//     console.log('Custom icon clicked!')
// })
