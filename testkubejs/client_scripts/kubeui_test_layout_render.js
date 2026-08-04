// Demonstrates KubeUI's advanced layout and rendering features: flexbox-style grow, absolute
// positioning + zIndex, split panes, custom backgrounds, responsive width, a real render-scale
// transform, slide/scale transitions with easing, screen-to-screen cross-fades, edge-snapping and
// minimizable draggable windows, non-modal multi-window support, and injecting a panel into a
// vanilla screen. Opened from the test menu (see kubeui_test_menu.js), which opens automatically
// on world join.

// Adds a small KubeUI panel to the vanilla pause (Esc) menu - a real screen this mod doesn't
// otherwise touch, so it doesn't compete with the sidebar's own InventoryScreen injection.
KubeUIScreenInjector.register('net.minecraft.client.gui.screens.PauseScreen', (screen, panel) => {
    panel.label('injectedHint', 'Injected by KubeUI')
        .anchor('bottom-right', 8, 8)
})

function openLayoutRenderDemo() {
    KubeUI.builder('Layout & Render Demo')
        .elementSize(280, 24)
        .draggable()
        .snapToEdges()
        .minimizable()
        .tab('Grow & Absolute', tab => tab
            .label('growHint', 'The right button grows to fill the row (try resizing the window wider).')
            .row(row => row
                .button('Fixed', screen => {}).width(80)
                .button('Grows', screen => {}).grow(1)
            ).width(260)
            .divider()
            .label('absoluteHint', 'A badge pinned to a fixed spot, above everything via zIndex.')
            .badge('Pinned', 0xFFE74C3C).absolute(230, 90).zIndex(10)
        )
        .tab('Split Pane', tab => tab
            .splitPane(260, 120, 0.4,
                left => left.label('leftLabel', 'Left pane').button('A button', screen => {}),
                right => right.label('rightLabel', 'Right pane').button('Another', screen => {}))
        )
        .tab('Background', tab => tab
            .label('bgHint', 'Reopen after picking a mode to see it applied.')
            .button('Dirt (default)', screen => {
                screen.close()
                openLayoutRenderDemo()
            })
            .button('Blur', screen => {
                screen.close()
                KubeUI.builder('Blurred').background('blur').draggable()
                    .label('info', 'Forced blur background.')
                    .button('Close', s => s.close())
                    .open()
            })
            .button('None (plain tint)', screen => {
                screen.close()
                KubeUI.builder('Plain').background('none').draggable()
                    .label('info', 'Lightest background option.')
                    .button('Close', s => s.close())
                    .open()
            })
        )
        .tab('Responsive Width', tab => tab
            .label('respHint', 'This label is 90% width on a small window, 300px otherwise.')
                .width({ default: 300, small: '90%', tiny: '95%' })
        )
        .tab('Render Scale', tab => tab
            .label('scaleHint', 'A whole extra tab opened at 1.4x real render scale (text included).')
            .button('Open scaled screen', screen => {
                KubeUI.builder('Scaled 1.4x')
                    .draggable()
                    .renderScale(1.4)
                    .label('info', 'Every pixel here, including this text, is really scaled up.')
                    .button('Close', s => s.close())
                    .open()
            })
        )
        .tab('Transitions', tab => tab
            .label('transHint', 'Cross-fades into a second screen instead of closing/reopening.')
            .button('Transition to another screen', screen => {
                screen.transitionTo(KubeUI.builder('Transitioned To')
                    .animated({ type: 'scale', easing: 'easeInOut', durationMs: 250 })
                    .label('info', 'Cross-faded in, then this screen itself scales/eases in.')
                    .button('Close', s => s.close()), 300)
            })
        )
        .tab('Non-Modal', tab => tab
            .label('nonModalHint', 'Opens extra independent draggable windows, all visible at once.')
            .button('Open a non-modal window', screen => {
                KubeUI.builder('Floating Window')
                    .draggable()
                    .nonModal()
                    .label('info', 'Drag me around - other windows stay put.')
                    .button('Close', s => s.close())
                    .open()
            })
        )
        .button('Close', screen => screen.close())
        .open()
}
