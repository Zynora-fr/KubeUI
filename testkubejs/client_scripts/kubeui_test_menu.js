// Central menu for every KubeUI test/demo screen - opens automatically on joining a world.
// Replaces the old per-screen keybinds (K/L/M/N) with a single hub, so there's nothing to
// remember and no keybind category to collide with.

function openKubeUITestMenu() {
    KubeUI.builder('KubeUI Test Menu')
        .label('info', 'Pick a demo to open:')
        .button('Widget Gallery (Phases 1-20)', screen => {
            screen.close()
            openKubeUITestScreen()
        })
        .button('UX Demo - tabs, drag, resize (Phases 21-30)', screen => {
            screen.close()
            openKubeUIUXDemo()
        })
        .button('Item Shop (Phases 31-40, client-trusted demo)', screen => {
            screen.close()
            openShop()
        })
        .button('Emerald Shop (real server-authoritative economy)', screen => {
            screen.close()
            openRealShop()
        })
        .button('Sizes Playground (Phases 31-40)', screen => {
            screen.close()
            openSizesDemo()
        })
        .button('Responsive/Scale Demo (%-width, auto-scroll, /kubeui scale)', screen => {
            screen.close()
            openResponsiveDemo()
        })
        .label('sidebarNote', 'Also: press E for the survival inventory - see the icon bar on its left edge.')
        .divider()
        .button('Close', screen => screen.close())
        .open()
}

ClientEvents.loggedIn(event => {
    openKubeUITestMenu()
})
