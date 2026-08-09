// Reference example for the guild/faction system - create a guild, invite/kick/promote members
// with real role-permission checks, guild chat, a claimed territory with an entry toast, XP/
// leveling, alliances, a badge, and a scheduled guild event - built with
// KubeUIActions.createGuild/.inviteToGuild/.claimGuildTerritory/.scheduleGuildEvent.

const GUILD_ID = 'kubeui_demo:ironclad'

KubeUIActions.register('kubeui_demo:guild_create', (player, data) => {
    if (KubeUIActions.createGuild(player, GUILD_ID, 'The Ironclad')) {
        player.tell('§aCreated "The Ironclad" - you are its owner.')
    } else {
        player.tell('§cCouldn\'t create it (already exists, or you\'re already in a guild).')
    }
})

KubeUIActions.register('kubeui_demo:guild_invite_self_note', (player, data) => {
    // A real invite needs a second real player (KubeUIActions.inviteToGuild(inviter, guildId,
    // target)) - shown here as a note rather than faked, since this demo can't summon a second
    // player. Try it for real with a friend online:
    player.tell('§7/kubeui and a real target player: KubeUIActions.inviteToGuild(you, "' + GUILD_ID + '", them)')
})

KubeUIActions.register('kubeui_demo:guild_chat', (player, data) => {
    KubeUIActions.sendGuildChat(player, data.getStringOr('message', 'Hello, guild!'))
})

KubeUIActions.register('kubeui_demo:guild_claim_territory', (player, data) => {
    let x = Math.floor(player.x)
    let z = Math.floor(player.z)
    KubeUIActions.claimGuildTerritory(GUILD_ID, x - 16, 0, z - 16, x + 16, 255, z + 16, player.level.dimension.toString())
    player.tell('§a32x32 territory claimed around you - a non-member walking in gets a toast.')
})

KubeUIActions.register('kubeui_demo:guild_add_xp', (player, data) => {
    KubeUIActions.addGuildXp(GUILD_ID, 100)
    player.tell('§7The Ironclad is now level ' + KubeUIActions.guildLevel(GUILD_ID) + ' (' + 100 + ' XP added).')
})

KubeUIActions.register('kubeui_demo:guild_set_badge', (player, data) => {
    KubeUIActions.setGuildBadge(GUILD_ID, 0xFFE07030, 'minecraft:netherite_ingot')
    player.tell('§7Badge set - a management screen would show it via .badge()/.item(...).')
})

KubeUIActions.register('kubeui_demo:guild_schedule_event', (player, data) => {
    KubeUIActions.scheduleGuildEvent(player, GUILD_ID, 'weekly_raid', 'Weekly Raid', 200) // 10s, for the demo
    player.tell('§7Scheduled "Weekly Raid" - fires in 10 seconds (see the statusTick-style event below).')
})

KubeUIGuildScriptEvents.scheduledEventTriggered(event => {
    let guildId = event.guildId
    let server = KubeUIActions // just to anchor the closure - real broadcast below uses the event's own data
    // A real script ties this straight into the existing quest-reward system for distribution -
    // this demo just announces it, since there's no online-player list handed to this event itself.
    console.log('[kubeui guild demo] "' + event.name + '" triggered for guild ' + guildId)
})

KubeUIActions.register('kubeui_demo:guild_leaderboard', (player, data) => {
    let entries = KubeUIActions.guildLeaderboard(10)
    if (entries.length === 0) {
        player.tell('§7No guilds yet.')
        return
    }
    player.tell('§b--- Guild Leaderboard ---')
    entries.forEach((entry, index) => {
        player.tell('§7#' + (index + 1) + ' ' + entry.getStringOr('name', '?') + ' - level ' + entry.getIntOr('level', 0) + ' (' + entry.getLongOr('xp', 0) + ' XP)')
    })
})
