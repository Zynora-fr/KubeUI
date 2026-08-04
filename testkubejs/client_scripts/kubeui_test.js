// Test script for the KubeUI addon - exercises every basic widget and every layout
// feature (row, grid, scrollPanel, list, anchor, width/height/align/padding).
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.

function openKubeUITestScreen() {
    KubeUI.builder('KubeUI Test')
        .label('status', 'Try each widget below')
        .row(row => row
            .button('Say Hello', screen => {
                console.log('Hello from KubeUI!')
                screen.setLabel('status', 'Hello from KubeUI!')
            }).width(90)
            .button('Close', screen => screen.close()).width(90).align('right')
        )
        .toggle('notify', 'Enable notifications', false, (screen, value) => {
            screen.setLabel('status', 'Notifications: ' + (value ? 'ON' : 'OFF'))
        })
        .textField('name', '', 'Type your name...', (screen, value) => {
            screen.setLabel('status', value.length > 0 ? ('Hi, ' + value + '!') : 'Type your name...')
        })
        .slider('volume', 0, 100, 50, (screen, value) => {
            screen.setLabel('status', 'Volume: ' + Math.round(value))
        })
        .dropdown('mode', ['Easy', 'Normal', 'Hard'], 'Normal', (screen, value) => {
            screen.setLabel('status', 'Mode: ' + value)
        })
        .grid(3, grid => grid
            .button('A', screen => screen.setLabel('status', 'Grid: A')).width(60)
            .button('B', screen => screen.setLabel('status', 'Grid: B')).width(60)
            .button('C', screen => screen.setLabel('status', 'Grid: C')).width(60)
            .button('D', screen => screen.setLabel('status', 'Grid: D')).width(60)
        )
        .number('count', 0, 10, 1, (screen, value) => {
            screen.setLabel('status', 'Count: ' + value)
        })
        .colorPicker('color', 0xFFFF5555, (screen, value) => {
            screen.setLabel('status', 'Color: #' + (value & 0xFFFFFF).toString(16).padStart(6, '0'))
        })
        .progressBar('progress', 3, 10)
        .divider()
        .scrollPanel(56, panel => panel
            .radioGroup('team', ['Red', 'Blue', 'Green'], 'Red', (screen, value) => {
                screen.setLabel('status', 'Team: ' + value)
            })
            .checkboxGroup('features', ['Fast', 'Strong', 'Lucky'], ['Fast'], (screen, values) => {
                screen.setLabel('status', 'Features: ' + values.join(', '))
            })
        )
        .list('fruits', ['Apple', 'Banana', 'Cherry'], (row, item, index) => {
            row.label('fruit_' + index, (index + 1) + '. ' + item).width(120)
            row.button('Pick', screen => screen.setLabel('status', 'Picked: ' + item)).width(60)
        })
        .item('minecraft:diamond', 4)
        .image('minecraft:textures/item/diamond.png', 32, 32).padding(0, 4)
        .textArea('notes', '', 'Write some notes...', 40, (screen, value) => {
            screen.setLabel('status', 'Notes: ' + value.length + ' chars')
        })
        .anchor('center')
        .open()
}
