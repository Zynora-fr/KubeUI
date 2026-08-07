// Reference example for the two quest objective types the gather/hunt/deliver chain
// (kubeui_quest_chain_example.js) doesn't already cover: `visit` (a real position/structure check,
// not just an inventory/kill count) and `xpLevel`, plus the fully generic "custom" hook - any
// objective `type` KubeUI doesn't build in itself is just a counter your own script bumps from
// whatever event it wants, no callback registration needed.

KubeUIActions.defineQuest('kubeui_demo:scout_village', {
    title: 'Scout the Village',
    description: 'Find a village and see it for yourself.',
    objectives: [
        { type: 'visit', structureTag: 'minecraft:village' },
    ],
    rewards: [
        { type: 'xp', levels: 1 },
    ],
})

KubeUIActions.defineQuest('kubeui_demo:rising_star', {
    title: 'Rising Star',
    description: 'Reach experience level 5.',
    objectives: [
        { type: 'xpLevel', target: 5 },
    ],
    rewards: [
        { type: 'item', item: 'minecraft:gold_ingot', count: 2 },
    ],
})

KubeUIActions.defineQuest('kubeui_demo:miners_warmup', {
    title: "Miner's Warmup",
    description: 'Break 20 stone or deepslate blocks to prove you know which end of the pickaxe to hold.',
    objectives: [
        // A `type` KubeUI has no built-in meaning for is just a real, persisted counter your own
        // script increments - see the BlockEvents.broken hook below. No progress-callback
        // registration needed; `KubeUIActions.incrementQuestObjective(...)` is the entire "generic
        // hook" this objective type needs.
        { id: 'stone', type: 'break_stone', target: 20, label: 'Stone/deepslate broken' },
    ],
    rewards: [
        { type: 'item', item: 'minecraft:iron_pickaxe', count: 1 },
    ],
})

// Both real stone-layer blocks - below Y=0 the overworld's "filler" stone is deepslate, not stone
// (verified against how this Minecraft version's overworld chunk generator actually fills terrain
// by depth), so a quest about "break stone with a pickaxe" that only listened for
// 'minecraft:stone' would silently never count progress for anyone mining underground, which is
// most players most of the time - a single BlockEvents.broken(...) call can take a list of targets
// at once, so both are covered by the exact same handler.
BlockEvents.broken(['minecraft:stone', 'minecraft:deepslate'], event => {
    KubeUIActions.incrementQuestObjective(event.getEntity(), 'kubeui_demo:miners_warmup', 'stone', 1)
})
