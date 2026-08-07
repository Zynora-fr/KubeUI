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
        .button('Theming & Accessibility Demo (presets, style, fontscale, Key widgets)', screen => {
            screen.close()
            openThemingA11yDemo()
        })
        .button('Networking & Config Demo (server push, permissions, config screen, ...)', screen => {
            screen.close()
            openNetworkingConfigDemo()
        })
        .button('DX Tools Demo (when/repeat, describe, lint, toJson/fromJson, dumpTree)', screen => {
            screen.close()
            openDxToolsDemo()
        })
        .button('Quest Board (advanced reference example - real server-side progress)', screen => {
            screen.close()
            openQuestBoard()
        })
        .button('Recipe Bridge Demo (recipeScreen, recipesFor, recipeSlot)', screen => {
            screen.close()
            openRecipeBridgeDemo()
        })
        .button('Trading Example (weighted trade pools, stock, quest-gated trades)', screen => {
            screen.close()
            openTradingExampleInfo()
        })
        .button('Quest Log (active/available/completed quests and progress)', screen => {
            screen.close()
            KubeUI.questLog()
        })
        .label('editorNote', 'In-game script editor: run /kubeui editor to browse/create/edit/delete files in kubejs/client_scripts.')
        .label('recipeDesignerNote', 'Define custom recipes in-game: run /kubeui recipe-designer, pick a kind, then craft in the real grid GUI and left-click the result to save.')
        .label('traderDesignerNote', 'Build a custom trader in-game: run /kubeui trader-designer, add trades and set AI/movement, then give yourself the egg and right-click a block to spawn it.')
        .label('questNote', 'Quests: run /quest (or /kubeui quest-log) to see progress, or /kubeui quest-editor to compose one without scripting.')
        .label('questChainNote', 'Try all 6 reference quests on one NPC (tagging replaces the list, so give them all at once):')
        .label('questChainCommand', '/kubeui tag-quest-giver @e[type=minecraft:villager,limit=1,sort=nearest] kubeui_demo:gather_wood,kubeui_demo:hunt_zombies,kubeui_demo:deliver_supplies,kubeui_demo:scout_village,kubeui_demo:rising_star,kubeui_demo:miners_warmup')
        .label('sidebarNote', 'Also: press E for the survival inventory - see the icon bar on its left edge.')
        .divider()
        .button('Close', screen => screen.close())
        .open()
}

ClientEvents.loggedIn(event => {
    openKubeUITestMenu()
})
