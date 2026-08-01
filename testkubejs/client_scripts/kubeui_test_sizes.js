// Exploratory test: how far can sizing be pushed? elementSize() changing mid-screen, per-element
// width()/height() overrides, extreme progress bar shapes, and a separate custom-font screen.
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.

function openSizesDemo() {
    KubeUI.builder('Sizes Playground')
        .label('title1', '-- Tiny (elementSize 60, 12) --')
        .elementSize(60, 12)
        .button('Tiny', s => s.setLabel('status', 'Tiny button clicked'))
        .toggle('tinyToggle', 'Tiny toggle', false, (s, v) => {})
        .slider('tinySlider', 0, 10, 5, (s, v) => s.setLabel('status', 'Tiny slider: ' + Math.round(v)))

        .label('title2', '-- Default (elementSize 200, 20) --')
        .elementSize(200, 20)
        .button('Default size button', s => s.setLabel('status', 'Default button clicked'))
        .slider('defaultSlider', 0, 100, 50, (s, v) => s.setLabel('status', 'Default slider: ' + Math.round(v)))

        .label('title3', '-- Huge (elementSize 320, 40) --')
        .elementSize(320, 40)
        .button('Huge Button', s => s.setLabel('status', 'Huge button clicked'))
        .slider('hugeSlider', 0, 100, 25, (s, v) => s.setLabel('status', 'Huge slider: ' + Math.round(v)))

        .elementSize(200, 20)
        .label('title4', '-- Per-element width() overrides, same row --')
        .row(row => row
            .button('W40', s => {}).width(40)
            .button('W80', s => {}).width(80)
            .button('W160', s => {}).width(160))

        .label('title5', '-- Per-element height() overrides, same row --')
        .row(row => row
            .button('Short', s => {}).height(12)
            .button('Normal', s => {}).height(20)
            .button('Tall', s => {}).height(40))

        .label('title6', '-- Extreme progress bars --')
        .progressBar('thin', 7, 10).height(4)
        .progressBar('thick', 3, 10).height(30)
        .progressBar('wide', 8, 10).width(320)
        .progressBar('narrow', 2, 10).width(40)

        .label('title7', '-- Alignment, same column width --')
        .label('alignLeft', 'Left').align('left')
        .label('alignCenter', 'Center').align('center')
        .label('alignRight', 'Right').align('right')

        .divider()
        .button('Open Custom Font Demo', s => openFontDemo())
        .button('Close', s => s.close())
        .open()
}

function openFontDemo() {
    // .font(...) applies to every label/button on the *whole* screen, not just what follows it -
    // it's a root-level (per-screen) setting, so it gets its own small screen here rather than
    // being mixed into the size comparisons above (where it'd just make the text hard to read).
    KubeUI.builder('Custom Font Demo')
        .font('minecraft:alt')
        .label('info1', 'This whole screen uses the "alt" font')
        .label('info2', '(the enchanting table / standard galactic alphabet)')
        .button('Close', s => s.close())
        .open()
}
