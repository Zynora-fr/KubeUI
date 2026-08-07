// Demo for the DX tooling added in Phases 191-199: .when()/.repeat() builder helpers,
// KubeUI.describe()/.lint()/.toJson()/.fromJson(), and screen.dumpTree() - open the console to see
// the printed output from each button.

function openDxToolsDemo() {
    let dxBuilder = KubeUI.builder('DX Tools Demo')
    let showWarning = true

    dxBuilder
        .label('info', 'Demonstrates .when()/.repeat()/describe()/lint()/toJson()/dumpTree()')
        .when(showWarning, b => b.label('warn', 'This label only exists because .when(true, ...) added it.'))
        .divider()
        .repeat(3, (b, i) => b.label('rep' + i, 'Repeated label #' + i + ' from .repeat(3, ...)'))
        .divider()
        .button('Print KubeUI.describe(this) to log', screen => {
            console.log(KubeUI.describe(dxBuilder))
        })
        .button('Print KubeUI.lint(this) to log', screen => {
            console.log('Lint issues: ' + JSON.stringify(KubeUI.lint(dxBuilder)))
        })
        .button('Print KubeUI.toJson(this) to log', screen => {
            console.log(KubeUI.toJson(dxBuilder))
        })
        .button('Roundtrip: KubeUI.fromJson(KubeUI.toJson(this))', screen => {
            let rebuilt = KubeUI.fromJson(KubeUI.toJson(dxBuilder))
            rebuilt.button('Close (roundtripped screen)', s => s.close())
            rebuilt.open()
        })
        .button('Print screen.dumpTree() to log', screen => {
            console.log(screen.dumpTree())
        })
        .button('Close', screen => screen.close())

    dxBuilder.open()
}
