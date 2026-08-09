// Reference example for the progression system - a three-branch
// combat skill tree with respec and real attribute effects.

// `icon` on a node is either a real item id (shows that item's own icon) or, for a genuinely
// custom texture, a resource path like 'kubeui:textures/gui/skills/berserker.png' pointing at a
// real PNG your own resource pack/KubeJS assets ship - both resolved client-side from the same
// field, no separate flag needed.
KubeUIActions.defineSkillTree('combat', {
    // Root nodes (no requires) - always available if the player has a point to spend.
    toughness1: { name: 'Toughness I', cost: 1, icon: 'minecraft:golden_apple', effects: [
        { attribute: 'minecraft:max_health', amount: 4, operation: 'add_value' },
    ] },
    swiftness1: { name: 'Swiftness I', cost: 1, icon: 'minecraft:feather', effects: [
        { attribute: 'minecraft:movement_speed', amount: 0.05, operation: 'add_multiplied_total' },
    ] },

    // Toughness branch
    toughness2: { name: 'Toughness II', cost: 2, requires: ['toughness1'], icon: 'minecraft:totem_of_undying', effects: [
        { attribute: 'minecraft:max_health', amount: 6, operation: 'add_value' },
    ] },

    // Two mutually-exclusive class specializations - unlocking one locks out the other
    // until a respec, via the shared 'classGroup'.
    berserker: { name: 'Berserker', cost: 2, requires: ['swiftness1'], classGroup: 'berserker', icon: 'minecraft:iron_sword', effects: [
        { attribute: 'minecraft:attack_damage', amount: 0.25, operation: 'add_multiplied_total' },
    ] },
    guardian: { name: 'Guardian', cost: 2, requires: ['swiftness1'], classGroup: 'guardian', icon: 'minecraft:shield', effects: [
        { attribute: 'minecraft:armor', amount: 4, operation: 'add_value' },
    ] },
}, {
    respecCurrency: 'gold', // reuses the economy system - respec costs gold, refunds points
    respecCostMultiplier: 3, // 3 gold per unlocked node
    pointsPerLevel: 1, // 1 skill point per real XP level gained
})

// The "quest" point source - a real quest reward can call this directly, no separate hook.
KubeUIActions.register('kubeui_demo:skills_bonus_points', (player, data) => {
    KubeUIActions.grantSkillPoints(player, 'combat', 3)
    player.tell('§a+3 combat skill points.')
})
