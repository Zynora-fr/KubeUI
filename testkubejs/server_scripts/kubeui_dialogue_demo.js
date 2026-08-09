// Reference example for the dialogue system - a branching "visual
// novel" conversation with a dynamic portrait, a timed choice, and two different `requires` gates
// (currency balance, quest state). Tag a nearby villager with it via:
//   /kubeui tag-dialogue-npc @e[type=minecraft:villager,limit=1,sort=nearest] kubeui_demo:sage
// or open it directly with no NPC via the client demo screen's own button.

KubeUIActions.defineDialogue('kubeui_demo:sage', 'greeting', {
    greeting: {
        text: 'Welcome, traveler. I am the Old Sage.',
        portrait: 'minecraft:villager',
        sound: 'minecraft:entity.villager.ambient',
        choices: [
            { label: 'Tell me about the ruins.', next: 'ruins' },
            { label: '[Requires 10 gold] Buy a lucky charm.', next: 'trade', requires: { currency: 'gold', amount: 10 } },
            { label: '[Requires an active "Gather Wood" quest] How goes the wood gathering?', next: 'quest_chat', requires: { quest: 'kubeui_demo:gather_wood', state: 'active' } },
            { label: 'Goodbye.', next: null },
        ],
    },
    ruins: {
        text: 'Long ago, a great civilization stood where the ruins lie. Few who explore them return.',
        portrait: 'minecraft:villager',
        choices: [{ label: 'Back', next: 'greeting' }],
    },
    // A timed choice - no answer within 8 seconds falls back to timeoutNext, exactly
    // as if the player had picked "Never mind." themselves.
    trade: {
        text: 'A wise choice - choose quickly, traveler.',
        portrait: 'minecraft:villager',
        timerSeconds: 8,
        timeoutNext: 'greeting',
        choices: [
            { label: 'Take the charm.', next: 'bought' },
            { label: 'Never mind.', next: 'greeting' },
        ],
    },
    // A dynamic portrait - swaps from the villager to the item itself.
    bought: {
        text: 'The charm is yours. May it serve you well.',
        portrait: 'minecraft:emerald',
        choices: [],
    },
    quest_chat: {
        text: 'Every log counts, traveler. Persevere.',
        portrait: 'minecraft:villager',
        choices: [{ label: 'Back', next: 'greeting' }],
    },
})

// Reads back whatever nodes this player has actually reached so far, persisted
// server-side across reconnects.
KubeUIActions.register('kubeui_demo:dialogue_history', (player, data) => {
    let nodes = KubeUIActions.dialogueHistory(player, 'kubeui_demo:sage')
    player.tell(nodes.length > 0 ? '§7Visited nodes: ' + nodes.join(', ') : '§7No conversation history yet - talk to the Sage first.')
})
