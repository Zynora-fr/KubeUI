// Client-side half of the dialogue reference example (see kubeui_dialogue_demo.js in
// server_scripts). Opening with no NPC (second argument left out) works fine - a dialogue doesn't
// need an associated entity, only a right-click-triggered one (via /kubeui tag-dialogue-npc) does.

function openDialogueDemo() {
    KubeUI.builder('Dialogue Demo')
        .elementSize(300, 20)
        .label('info', 'A branching NPC conversation - typewriter text, a timed choice, and gates on both gold and quest state.')
        .divider()
        .button('Talk to the Old Sage', screen => {
            screen.close()
            KubeUI.dialogue('kubeui_demo:sage', null)
        })
        .button('Show my conversation history', screen => screen.runServerAction('kubeui_demo:dialogue_history', null))
        .divider()
        .label('npcNote', 'To trigger it by right-clicking a real NPC instead:')
        .label('npcCommand', '/kubeui tag-dialogue-npc @e[type=minecraft:villager,limit=1,sort=nearest] kubeui_demo:sage')
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
