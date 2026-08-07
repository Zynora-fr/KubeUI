// Demonstrates KubeUI's theming and accessibility features: named/custom theme presets with a
// fade transition, per-element .style() color overrides, a text-only font scale, Key-suffixed
// widgets resolved from the language file, and the sidebar icon pack override.
// Opened from the test menu (see kubeui_test_menu.js), which opens automatically on world join.
//
// Also worth trying manually while this screen (or any KubeUI screen) is open:
// - Tab to the rating/range slider/keybind field below, then use arrow keys/Enter - every one of
//   them is fully keyboard-operable and draws a visible focus outline.
// - /kubeui fontscale 1.5 and /kubeui theme preview dark from chat.

KubeUI.registerThemePreset('kubeui:oceanic', 0xFFEAF3F3, 0xFF2266CC, 0xFFDCEFFF)

function openThemingA11yDemo() {
    KubeUI.builder('Theming & Accessibility Demo')
        .elementSize(260, 24)
        .tab('Theme presets', tab => tab
            .label('themeHint', 'Changing the theme fades over 300ms instead of snapping.')
            .row(row => row
                .button('Default', () => KubeUI.setTheme('default')).width(80)
                .button('Dark', () => KubeUI.setTheme('dark')).width(80)
                .button('Light', () => KubeUI.setTheme('light')).width(80))
            .row(row => row
                .button('High contrast', () => KubeUI.setTheme('high-contrast')).width(120)
                .button('Custom (oceanic)', () => KubeUI.setTheme('kubeui:oceanic')).width(120))
            .divider()
            .progressBar('themedBar', 65, 100)
            .chart('themedChart', 'bar', [3, 7, 4, 9, 5], ['A', 'B', 'C', 'D', 'E'])
        )
        .tab('Per-element style', tab => tab
            .label('styleHint', 'This chart overrides its own colors - unaffected by the theme buttons above.')
            .chart('styledChart', 'bar', [4, 2, 8, 5], ['W', 'X', 'Y', 'Z'])
                .style({color: 0xFFFFD700, accent: 0xFFCC5DE8})
            .progressBar('styledBar', 40, 100)
                .style({accent: 0xFFCC5DE8})
        )
        .tab('Font scale', tab => tab
            .label('fontHint', 'Only KubeUI\'s own custom-drawn text scales - box sizes stay put.')
            .row(row => row
                .button('0.75x', () => { KubeUI.setFontScale(0.75); })
                .button('1x', () => { KubeUI.resetFontScale(); })
                .button('1.5x', () => { KubeUI.setFontScale(1.5); }))
            .richText('scaledText', Text.of('This richText scales.').yellow())
            .table('scaledTable', ['Col A', 'Col B'], [100, 100], [['1', '2'], ['3', '4']])
        )
        .tab('Translated (Key) widgets', tab => tab
            .label('keyHint', 'No translation registered for these keys, so the fallback text shows.')
            .buttonKey('kubeui.demo.hello', 'Hello (buttonKey)', () => {})
            .toggleKey('demoToggle', 'kubeui.demo.toggle', 'Enable thing (toggleKey)', false, () => {})
            .textFieldKey('demoField', '', 'kubeui.demo.hint', 'Type here... (textFieldKey)', () => {})
            .badgeKey('kubeui.demo.badge', 'New (badgeKey)', 0xFF3B6EA5)
            .button('Hover me', () => {}).tooltipKey('kubeui.demo.tooltip', 'A translated tooltip (tooltipKey)')
        )
        .tab('Keyboard & focus', tab => tab
            .label('kbHint', 'Tab to each of these, then use arrow keys/Enter - watch for the focus outline.')
            .rating('kbRating', 5, 3, () => {})
            .rangeSlider('kbRange', 0, 100, 20, 80, () => {})
            .keybindCapture('kbKeybind', 0, () => {})
            .divider()
            .selectableList('kbSelectable', ['Row 1', 'Row 2', 'Row 3'], (row, item) => row.label('kbrow_' + item, item), () => {})
        )
        .button('Close', screen => screen.close())
        .open()
}
