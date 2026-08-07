// Client half of the quest board reference example - see the server half
// (server_scripts/kubeui_quest_board_example.js) for where the real decisions are made and the
// real turn-in requirement is enforced. This side only builds the screen and sends a quest id when
// a button is clicked; it never decides whether a quest can be accepted/completed, nor what's
// actually taken from or given to the player - "requires"/"reward" below are just for display, the
// server has its own copy it actually trusts.

const QUEST_BOARD_QUESTS = [
    { id: 'gather_wood', name: 'Gather Wood', requires: '10x Oak Log', reward: 'Emerald' },
    { id: 'slay_zombies', name: 'Slay Zombies', requires: '5x Rotten Flesh', reward: 'Iron Ingot' },
    { id: 'explore_cave', name: 'Explore a Cave', requires: '1x Torch', reward: 'Diamond' },
]

function openQuestBoard() {
    let b = KubeUI.builder('Quest Board')
        .draggable()
        .elementSize(280, 20)

    b.label('info', 'Accept a quest, gather the items, then Complete to turn them in.')
    b.divider()

    QUEST_BOARD_QUESTS.forEach(quest => {
        b.label('name_' + quest.id, quest.name)
        b.label('req_' + quest.id, '  Turn in: ' + quest.requires + '  ->  Reward: ' + quest.reward)
        b.row(row => {
            row.button('Accept', screen => screen.runServerAction('kubeui_quest:accept', { questId: quest.id })).width(130)
            row.button('Complete', screen => screen.runServerAction('kubeui_quest:complete', { questId: quest.id })).width(130)
        })
        b.divider()
    })

    b.button('Close', screen => screen.close())
    b.open()
}
