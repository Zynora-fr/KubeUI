// Reference example for the structures/dungeon/loot systems - a real vanilla loot table preview,
// a small 3-room dungeon with real progression locking, a scriptable weighted loot table, a
// delayed-reveal loot chest, group loot voting, and a completion-time leaderboard - built with
// KubeUIActions.lootTableBrowser/.defineDungeon/.defineLootTable/.rollLoot/.startLootVote.

KubeUIActions.defineDungeon('kubeui_demo:crypt', 'The Sunken Crypt', ['entrance', 'hall', 'boss_room'])

KubeUIActions.defineLootTable('kubeui_demo:crypt_loot', [
    { item: 'minecraft:iron_ingot', count: 2, weight: 6 },
    { item: 'minecraft:emerald', count: 1, weight: 3 },
    { item: 'minecraft:diamond', count: 1, weight: 1 },
])

// A generous 32x32 box around world origin, purely so the demo has *some* real coordinates to
// check against without needing the player to go find/build an actual structure first.
KubeUIActions.defineStructure('kubeui_demo:crypt_zone', 'The Sunken Crypt', 'Medium', -16, 0, -16, 16, 255, 16, 'minecraft:overworld')

function giveItem(player, itemId, count) {
    let leftover = player.inventory.insertItem(Item.of(itemId, count), false)
    if (leftover.getCount() > 0) {
        player.drop(leftover, false)
    }
}

// A real vanilla loot table id - "probable" contents only, see KubeUIActions.lootTableBrowser's
// own doc for why this is a sampled approximation, never a guaranteed-drop list.
KubeUIActions.register('kubeui_demo:dungeon_preview_loot', (player, data) => {
    let counts = KubeUIActions.lootTableBrowser(player, 'minecraft:chests/simple_dungeon')
    let entries = Object.keys(counts)
    if (entries.length === 0) {
        player.tell('§7No sample results (unknown loot table, or it rolled empty every time).')
        return
    }
    player.tell('§b--- Simple Dungeon Chest: probable contents (200 sampled rolls, not guaranteed) ---')
    entries.sort((a, b) => counts[b] - counts[a]).forEach(item => {
        player.tell('§7' + item + ': appeared in ' + counts[item] + '/200 rolls')
    })
})

KubeUIActions.register('kubeui_demo:dungeon_enter_room', (player, data) => {
    let roomId = data.getStringOr('room', '')
    if (KubeUIActions.markDungeonRoomVisited('kubeui_demo:crypt', player, roomId)) {
        player.tell('§aEntered "' + roomId + '".')
    } else {
        player.tell('§cThat room is locked - visit the previous room first.')
    }
})

KubeUIActions.register('kubeui_demo:dungeon_open_chest', (player, data) => {
    KubeUIActions.markDungeonChestOpened('kubeui_demo:crypt', player, 'hall_chest')
    let stack = KubeUIActions.rollLoot('kubeui_demo:crypt_loot', 1.0)
    if (stack.isEmpty()) {
        player.tell('§7The chest was empty.')
        return
    }
    let itemId = stack.getId()
    KubeUIActions.openRemote(player, 'kubeui_demo:dungeon_chest_result', { item: itemId, count: stack.getCount() })
    giveItem(player, itemId, stack.getCount())
})

KubeUIActions.register('kubeui_demo:dungeon_defeat_boss', (player, data) => {
    if (KubeUIActions.markDungeonBossDefeated('kubeui_demo:crypt', player)) {
        player.tell('§aThe Sunken Crypt is cleared! Check the leaderboard.')
    } else {
        player.tell('§cVisit every room first (entrance -> hall -> boss_room).')
    }
})

KubeUIActions.register('kubeui_demo:dungeon_progress_request', (player, data) => {
    let progress = KubeUIActions.dungeonProgress('kubeui_demo:crypt', player)
    player.tell('§b--- ' + progress.getStringOr('name', '?') + ' ---')
    progress.getListOrEmpty('rooms').forEach(room => {
        let status = room.getBooleanOr('visited', false) ? '§aVisited' : (room.getBooleanOr('unlocked', false) ? '§eUnlocked' : '§7Locked')
        player.tell(status + '§7 - ' + room.getStringOr('id', '?'))
    })
    player.tell('§7Chests opened: ' + progress.getIntOr('chestsOpened', 0) + '  Boss defeated: ' + progress.getBooleanOr('bossDefeated', false))
})

KubeUIActions.register('kubeui_demo:dungeon_leaderboard_request', (player, data) => {
    let entries = KubeUIActions.dungeonLeaderboard('kubeui_demo:crypt', 10)
    if (entries.length === 0) {
        player.tell('§7No completions recorded yet.')
        return
    }
    player.tell('§b--- Sunken Crypt Leaderboard ---')
    entries.forEach((entry, index) => {
        player.tell('§7#' + (index + 1) + ' ' + entry.getStringOr('playerName', '?') + ' - ' + Math.round(entry.getLongOr('timeMs', 0) / 1000) + 's')
    })
})

KubeUIActions.register('kubeui_demo:dungeon_reset', (player, data) => {
    KubeUIActions.resetDungeon('kubeui_demo:crypt')
    player.tell('§7The Sunken Crypt has been reset for everyone.')
})

// Group loot voting - a real multi-participant vote needs more than one online player to actually
// contest; with just yourself, a "need" vote always wins immediately, which still exercises the
// real server-side resolution path honestly (not faked), just not a real multiplayer contest.
KubeUIActions.register('kubeui_demo:dungeon_start_vote', (player, data) => {
    let voteId = KubeUIActions.startLootVote('minecraft:diamond', 1, [player])
    player.tell('§7Vote started (id ' + voteId + ') - cast "Need" or "Greed" below.')
    KubeUIActions.playerData(player).putString('kubeui_demo_vote_id', voteId)
})

KubeUIActions.register('kubeui_demo:dungeon_cast_vote', (player, data) => {
    let voteId = KubeUIActions.playerData(player).getStringOr('kubeui_demo_vote_id', '')
    if (!voteId) {
        player.tell('§cStart a vote first.')
        return
    }
    let winner = KubeUIActions.castLootVote(voteId, player, data.getStringOr('choice', 'pass'))
    if (winner) {
        player.tell('§a' + winner + ' won the diamond!')
    } else {
        player.tell('§7Vote recorded - waiting on other participants.')
    }
})
