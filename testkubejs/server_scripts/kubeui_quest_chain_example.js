// Reference example for the quest system - a real, 3-quest chain (gather -> hunt -> deliver to
// an NPC), each one only available once the previous is completed. Every objective type the
// engine understands built-in is used at least once here: `collect` (gather/deliver) and `kill`
// (hunt) - `visit`/`xpLevel` and a fully custom counter type are demonstrated separately in
// kubeui_quest_objective_types_example.js so this one stays focused on the chain itself.
//
// Try it: tag any entity as this chain's quest giver with
//   /kubeui tag-quest-giver @e[type=minecraft:villager,limit=1,sort=nearest] kubeui_demo:gather_wood,kubeui_demo:hunt_zombies,kubeui_demo:deliver_supplies
// then right-click it - KubeUI opens the Quest Giver screen automatically, no client script
// needed for that part at all. Progress on `collect`/`kill` objectives is always real (your
// actual inventory, your actual kills), and everything survives closing the game entirely - quest
// progress lives on your own player data, quest definitions like these are re-declared by this
// script every time the server starts.

KubeUIActions.defineQuest('kubeui_demo:gather_wood', {
    title: 'Gather Wood',
    description: 'The village elder needs building supplies. Bring back 10 oak logs.',
    objectives: [
        { type: 'collect', item: 'minecraft:oak_log', target: 10 },
    ],
    rewards: [
        { type: 'item', item: 'minecraft:emerald', count: 3 },
        { type: 'xp', levels: 1 },
    ],
})

KubeUIActions.defineQuest('kubeui_demo:hunt_zombies', {
    title: 'Clear the Woods',
    description: 'Zombies have been spotted near the tree line. Deal with 5 of them.',
    requires: ['kubeui_demo:gather_wood'],
    objectives: [
        { type: 'kill', entity: 'minecraft:zombie', target: 5, label: 'Zombies slain' },
    ],
    rewards: [
        { type: 'item', item: 'minecraft:emerald', count: 5 },
        { type: 'xp', levels: 2 },
    ],
})

KubeUIActions.defineQuest('kubeui_demo:deliver_supplies', {
    title: 'Deliver Supplies',
    description: 'With the woods safer, bring 5 loaves of bread back to the elder to restock the village.',
    requires: ['kubeui_demo:hunt_zombies'],
    objectives: [
        { type: 'collect', item: 'minecraft:bread', target: 5 },
    ],
    rewards: [
        { type: 'item', item: 'minecraft:diamond', count: 1 },
        { type: 'command', command: 'say @s has become a trusted friend of the village!' },
    ],
})
