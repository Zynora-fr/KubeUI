// Advanced reference example - a small but complete quest board built with KubeUI, not just a UI
// mockup: real per-player progress (KubeUIActions.playerData, survives a reconnect or server
// restart) and every decision (can this quest be accepted/completed, what's actually taken/given)
// made here, server-side. The client (see the matching client_scripts file) only ever sends a
// quest id - it can't lie about having completed a quest it never accepted, or about having handed
// in items it doesn't actually have, because the server never trusts anything the client claims.

const QUESTS = {
    gather_wood: { name: 'Gather Wood', requires: 'minecraft:oak_log', requiresCount: 10, reward: 'minecraft:emerald' },
    slay_zombies: { name: 'Slay Zombies', requires: 'minecraft:rotten_flesh', requiresCount: 5, reward: 'minecraft:iron_ingot' },
    explore_cave: { name: 'Explore a Cave', requires: 'minecraft:torch', requiresCount: 1, reward: 'minecraft:diamond' },
}

function questKey(id) {
    return 'quest_' + id
}

// Safety-guarded "keep pulling from matched slots until the full amount is gone" - a stack can
// be split across several slots, so a single extractItem() call isn't enough on its own.
function removeItems(player, itemId, amount) {
    let remaining = amount
    let inventory = player.inventory
    let safetyGuard = 0

    while (remaining > 0 && safetyGuard < 64) {
        safetyGuard++

        let slot = inventory.find(itemId)
        if (slot < 0) return false

        let take = Math.min(remaining, inventory.getStackInSlot(slot).getCount())
        inventory.extractItem(slot, take, false)
        remaining -= take
    }

    return remaining <= 0
}

KubeUIActions.register('kubeui_quest:accept', { questId: 'string' }, 500, (player, data) => {
    let questId = data.getStringOr('questId', '')
    let quest = QUESTS[questId]
    if (!quest) {
        player.tell('§cUnknown quest: ' + questId)
        return
    }

    let stored = KubeUIActions.playerData(player)
    if (stored.contains(questKey(questId))) {
        player.tell('§eYou already accepted (or completed) "' + quest.name + '".')
        return
    }

    stored.putString(questKey(questId), 'accepted')
    player.tell('§aQuest accepted: ' + quest.name + ' - bring ' + quest.requiresCount + 'x ' + quest.requires + ' to turn in.')
})

KubeUIActions.register('kubeui_quest:complete', { questId: 'string' }, 500, (player, data) => {
    let questId = data.getStringOr('questId', '')
    let quest = QUESTS[questId]
    if (!quest) {
        player.tell('§cUnknown quest: ' + questId)
        return
    }

    let stored = KubeUIActions.playerData(player)
    let state = stored.getStringOr(questKey(questId), '')
    if (state !== 'accepted') {
        player.tell('§cAccept "' + quest.name + '" before trying to complete it.')
        return
    }

    let owned = player.inventory.count(quest.requires)
    if (owned < quest.requiresCount) {
        player.tell('§cYou need ' + quest.requiresCount + 'x ' + quest.requires + ' to turn in "' + quest.name + '" - you have ' + owned + '.')
        return
    }

    // Simulate the reward first: if there's no room for it, don't take the player's turn-in items
    // for nothing - same "simulate before mutating" order kubeui_shop_real.js already uses.
    let leftover = player.inventory.insertItem(Item.of(quest.reward), true)
    if (leftover.getCount() > 0) {
        player.tell('§cYour inventory is full - make room before completing "' + quest.name + '".')
        return
    }

    if (!removeItems(player, quest.requires, quest.requiresCount)) {
        player.tell('§cSomething went wrong turning in items - completion cancelled, nothing was taken.')
        return
    }

    stored.putString(questKey(questId), 'completed')
    player.inventory.insertItem(Item.of(quest.reward), false)
    player.tell('§aQuest completed: ' + quest.name + '! Turned in ' + quest.requiresCount + 'x ' + quest.requires + ', reward: ' + quest.reward)
})
