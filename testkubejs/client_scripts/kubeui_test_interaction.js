// Demonstrates KubeUI's advanced interaction features: generic drag-and-drop between widgets,
// per-screen hotkeys, a context menu with dynamically computed items, undoable text fields, scroll
// momentum, double-click actions, delayed hover previews, and screen.shake() for refused actions.
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.

let dropCount = 0
let recentContextItems = ['Refresh']

function openInteractionDemo() {
    KubeUI.builder('Interaction Demo')
        .elementSize(280, 24)
        .draggable()
        .hotkey('R', screen => {
            dropCount = 0
            screen.setLabel('dropStatus', 'Dropped: 0 (reset with R)')
        })
        .tab('Drag & Drop', tab => tab
            .label('dragHint', 'Drag the item below onto the drop zone. Press R to reset the count.')
            .item('minecraft:diamond', 1)
                .draggableFrom('diamond')
            .badge('Drop here', 0xFF3B6EA5)
                .dropTarget((screen, payload) => {
                    dropCount++
                    screen.setLabel('dropStatus', 'Dropped: ' + dropCount + ' (last: ' + payload + ')')
                })
            .label('dropStatus', 'Dropped: 0 (reset with R)')
        )
        .tab('Context Menu & Double-Click', tab => tab
            .label('ctxHint', 'Right-click: menu items are recomputed every time it opens.')
            .button('Right-click me', screen => {})
                .contextMenu(screen => recentContextItems.slice(), (screen, item) => {
                    recentContextItems.unshift(item === 'Refresh' ? 'Refreshed!' : item)
                    recentContextItems = recentContextItems.slice(0, 4)
                    screen.setLabel('ctxStatus', 'Picked: ' + item)
                })
            .label('ctxStatus', 'Picked: (nothing yet)')
            .divider()
            .label('dblHint', 'Double-click the button below for a distinct action.')
            .button('Single or double-click me', screen => screen.setLabel('dblStatus', 'Single click'))
                .onDoubleClick(screen => screen.setLabel('dblStatus', 'Double click!'))
            .label('dblStatus', 'Single click')
        )
        .tab('Undo & Hover Preview', tab => tab
            .label('undoHint', 'Ctrl+Z / Ctrl+Y to undo/redo this field.')
            .textField('undoableField', 'Edit me...', '', (screen, value) => {})
                .undoable()
            .divider()
            .label('hoverHint', 'Hover the badge below for a second to see a preview popover.')
            .badge('Hover me', 0xFF8E44AD)
                .hoverPreview(600, preview => preview
                    .label('previewLine1', 'A richer preview than a plain tooltip.')
                    .label('previewLine2', 'Dismissed as soon as you move away.'))
        )
        .tab('Momentum & Shake', tab => tab
            .label('momentumHint', 'Scroll and release - the list keeps gliding briefly.')
            .scrollPanel(120, panel => {
                for (let i = 1; i <= 30; i++) {
                    panel.label('momentumRow' + i, 'Row ' + i)
                }
            })
            .divider()
            .button('Trigger a refused-action shake', screen => screen.shake())
        )
        .button('Close', screen => screen.close())
        .open()
}
