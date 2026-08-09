// Client-side half of the combat reference example (see kubeui_combat_demo.js in server_scripts).
// Every button here is just a runServerAction round trip - the boss bar/status/cooldown/combat-log
// HUD overlays render on their own once the server pushes state, no client-side polling needed.

function openCombatDemo() {
    KubeUI.builder('Combat Demo')
        .elementSize(280, 20)
        .label('info', 'A scriptable boss fight - watch the bar top-center change color/text by phase.')
        .button('Start Boss Fight', screen => screen.runServerAction('kubeui_demo:combat_start_boss', null))
        .button('Attack', screen => screen.runServerAction('kubeui_demo:combat_attack_boss', null))
        .divider()
        .label('statusInfo', 'Attacking applies "Enflammé de rage" (see the buff row, left edge) -')
        .label('statusInfo2', 'costs 1 gold/second while active. Use the Economy Demo to earn some first.')
        .divider()
        .button('Use Power Strike (5s cooldown)', screen => screen.runServerAction('kubeui_demo:combat_use_skill', null))
        .divider()
        .toggle('combatLog', 'Combat log HUD (bottom-right)', true, (screen, value) => {
            screen.runServerAction('kubeui_demo:combat_toggle_log', { enabled: value })
        })
        .button('Show Attack Range (5 blocks)', screen => screen.runServerAction('kubeui_demo:combat_show_aoe', null))
        .button('Hide Attack Range', screen => screen.runServerAction('kubeui_demo:combat_hide_aoe', null))
        .divider()
        .button('Combat History', screen => {
            screen.close()
            screen.runServerAction('kubeui_demo:combat_history_request', null)
        })
        .button('Risky Action (needs confirmation)', screen => {
            KubeUI.confirm(
                'Are you sure?',
                'This spends 1 gold you can\'t get back. Continue?',
                () => screen.runServerAction('kubeui_demo:combat_risky_action', null),
                () => {}
            )
        })
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
