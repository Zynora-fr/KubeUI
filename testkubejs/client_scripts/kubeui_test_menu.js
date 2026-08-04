// Central menu for every KubeUI test/demo screen - opens automatically on joining a world.
// Replaces the old per-screen keybinds (K/L/M/N) with a single hub, so there's nothing to
// remember and no keybind category to collide with.

function openKubeUITestMenu() {
    KubeUI.builder('KubeUI Test Menu')
        .label('info', 'Pick a demo to open:')
        .button('Widget Gallery', screen => {
            screen.close()
            openKubeUITestScreen()
        })
        .button('UX Demo - tabs, drag, resize', screen => {
            screen.close()
            openKubeUIUXDemo()
        })
        .button('Item Shop (client-trusted demo)', screen => {
            screen.close()
            openShop()
        })
        .button('Emerald Shop (real server-authoritative economy)', screen => {
            screen.close()
            openRealShop()
        })
        .button('Sizes Playground', screen => {
            screen.close()
            openSizesDemo()
        })
        .button('Responsive/Scale Demo (%-width, auto-scroll, /kubeui scale)', screen => {
            screen.close()
            openResponsiveDemo()
        })
        .button('More Widgets Demo', screen => {
            screen.close()
            openKubeUIWidgets2Demo()
        })
        .button('Data Widgets Demo (table, tree, chart, map, ...)', screen => {
            screen.close()
            openKubeUIDataWidgetsDemo()
        })
        .button('Layout & Render Demo (grow, split pane, scale, transitions, ...)', screen => {
            screen.close()
            openLayoutRenderDemo()
        })
        .button('Interaction Demo (drag & drop, hotkeys, undo, momentum, ...)', screen => {
            screen.close()
            openInteractionDemo()
        })
        .label('sidebarNote', 'Also: press E for the survival inventory - see the icon bar on its left edge.')
        .divider()
        .button('Close', screen => screen.close())
        .open()
}

ClientEvents.loggedIn(event => {
    openKubeUITestMenu()
})
