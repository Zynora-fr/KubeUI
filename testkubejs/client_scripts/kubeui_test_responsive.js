// Demo of the responsive/scale features: percentage-based sizing (stays proportional on any
// window size/resolution, instead of a fixed pixel count that looks huge on one player's screen
// and tiny on another's), the automatic scroll safety net (a screen taller than the window
// scrolls instead of getting cut off - try shrinking the window or lowering GUI Scale to see it
// kick in on a screen that normally fits), and the personal /kubeui scale knob.

function openResponsiveDemo() {
    let builder = KubeUI.builder('Responsive Demo')
        .label('info', 'These two rows are 90%/45% of the window width - resize the window and they follow.')
        .divider()

    builder.row(row => {
        row.button('90% wide', screen => {}).width('90%')
    })

    builder.row(row => {
        row.button('45%', screen => {}).width('45%')
        row.button('45%', screen => {}).width('45%')
    })

    builder
        .divider()
        .label('scaleInfo', 'Current KubeUI scale: ' + KubeUI.getScale() + ' (also settable with /kubeui scale <factor>)')
        .row(row => {
            row.button('Scale 0.75x', screen => { KubeUI.setScale(0.75); screen.close(); openResponsiveDemo() })
            row.button('Scale 1x (default)', screen => { KubeUI.resetScale(); screen.close(); openResponsiveDemo() })
            row.button('Scale 1.5x', screen => { KubeUI.setScale(1.5); screen.close(); openResponsiveDemo() })
        })
        .divider()
        .label('overflowInfo', 'Below: 30 rows, deliberately taller than most windows - watch it auto-scroll instead of getting cut off.')

    for (let i = 1; i <= 30; i++) {
        builder.label('row_' + i, 'Row ' + i + ' of 30 - this screen was never marked .resizable()')
    }

    builder
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
