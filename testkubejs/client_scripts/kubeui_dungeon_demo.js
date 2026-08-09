// Client-side half of the dungeon reference example (see kubeui_dungeon_demo.js in server_scripts).

// The delayed-reveal loot chest (purely visual, on the already-delivered item widget) - the real
// roll already happened server-side (KubeUIActions.rollLoot) and the item's already in your
// inventory; this just adds a few real ticks of "suspense" before naming what you got, using the
// same real onTick/setLabel mechanism the dialogue typewriter effect already proved out.
KubeUIRemoteScreens.register('kubeui_demo:dungeon_chest_result', data => {
    let itemId = data.getStringOr('item', '')
    let count = data.getIntOr('count', 1)
    let revealed = false
    let ticks = 0
    const REVEAL_AT_TICKS = 30

    KubeUI.builder('A Legendary Chest...')
        .label('suspense', 'The chest creaks open...')
        .item(itemId, count)
        .onTick(screen => {
            ticks++
            if (!revealed && ticks >= REVEAL_AT_TICKS) {
                revealed = true
                screen.setLabel('suspense', 'You found ' + count + 'x ' + itemId + '! (already in your inventory)')
            }
        })
        .button('Close', screen => screen.close())
        .open()
})

function openDungeonDemo() {
    KubeUI.builder('Dungeon Demo')
        .elementSize(280, 20)
        .label('info', 'The Sunken Crypt: entrance -> hall -> boss_room, in order.')
        .button('Preview Real Loot Table (simple_dungeon chest)', screen => screen.runServerAction('kubeui_demo:dungeon_preview_loot', null))
        .divider()
        .button('Enter: entrance', screen => screen.runServerAction('kubeui_demo:dungeon_enter_room', { room: 'entrance' }))
        .button('Enter: hall', screen => screen.runServerAction('kubeui_demo:dungeon_enter_room', { room: 'hall' }))
        .button('Open Hall Chest (scriptable weighted loot)', screen => screen.runServerAction('kubeui_demo:dungeon_open_chest', null))
        .button('Enter: boss_room', screen => screen.runServerAction('kubeui_demo:dungeon_enter_room', { room: 'boss_room' }))
        .button('Defeat Boss', screen => screen.runServerAction('kubeui_demo:dungeon_defeat_boss', null))
        .divider()
        .button('My Progress', screen => screen.runServerAction('kubeui_demo:dungeon_progress_request', null))
        .button('Leaderboard', screen => screen.runServerAction('kubeui_demo:dungeon_leaderboard_request', null))
        .divider()
        .label('voteInfo', 'Group loot vote (real resolution, but needs 2+ online players to actually contest):')
        .button('Start Vote (1 diamond)', screen => screen.runServerAction('kubeui_demo:dungeon_start_vote', null))
        .button('Vote Need', screen => screen.runServerAction('kubeui_demo:dungeon_cast_vote', { choice: 'need' }))
        .button('Vote Greed', screen => screen.runServerAction('kubeui_demo:dungeon_cast_vote', { choice: 'greed' }))
        .divider()
        .button('Reset Dungeon', screen => {
            KubeUI.confirm(
                'Reset The Sunken Crypt?',
                'This clears everyone\'s room/chest/boss progress. Continue?',
                () => screen.runServerAction('kubeui_demo:dungeon_reset', null),
                () => {}
            )
        })
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
