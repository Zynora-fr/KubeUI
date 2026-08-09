// Real slash commands for guilds/parties/housing, built entirely in this script via KubeJS's own
// ServerEvents.commandRegistry - same real mechanism kubeui_admin_commands_example.js already
// showed, used here instead of new Java command classes on purpose ("Toutes les commandes doivent
// être créées en JS dans les scripts, pas de Java" - real, explicit ask). Every command below is a
// thin wrapper around KubeUIActions methods that already existed (guilds/parties/homes/claims),
// only reachable through GUI buttons before this - none of this needed a single Java change beyond
// the handful of small additions KubeUIActions needed anyway (renameGuild/guildNameOf/claimsOf/
// leaderboard access), all already-public methods.

const HOME_TP_COOLDOWN_TICKS = 100 // 5 real seconds - "avoir un cooldown" (real ask), keeps
// `/home tp` from being a free, instant, spammable escape button in combat.

// Guild tag in regular chat - "dès qu'on parle dans le tchat sa dit notre guild" (real ask), and
// entirely a script's own decision, not hardcoded in Java ("vraiment tout en JS" - real ask): the
// Java side (KubeUIChatEvents) only posts a neutral `KubeUIChatScriptEvents.decorate` event with
// the message text - THIS handler is what actually prefixes the guild tag. Change the format,
// stack more decorations on top (a rank icon, a level badge, ...), or delete this whole block to
// turn the feature off entirely - all without touching a single Java file.
KubeUIChatScriptEvents.decorate(event => {
    let guildName = KubeUIActions.guildNameOf(event.player)
    if (guildName !== '') {
        event.setMessage('§d[' + guildName + ']§r ' + event.getMessage())
    }
})

// Real, reported crash this guards against: `java.util.UUID.fromString(...)` throws
// `ReferenceError: "java" is not defined` - KubeJS's Rhino sandbox doesn't expose the bare `java`
// package global at all (unlike some other Rhino embeddings). The real way to parse a UUID string
// from a script is the dedicated `UUID` global KubeJS itself binds (`UUID.fromString(...)`, used
// everywhere below) - not `java.util.UUID`.

// ---------------------------------------------------------------- GUI data helpers
//
// Bare `/guild`, `/party`, `/home` and `/claims` (no subcommand) open a real GUI instead of
// printing to chat ("fait en sorte d'avoir un beau GUI... on voit tout dans le GUI" - real ask) -
// see kubeui_default_screens.js (client_scripts) for the screens themselves. Every button in those
// screens calls back into one of the KubeUIActions.register(...) handlers below, which mutates
// state then re-sends fresh data to the same screen id so it updates live rather than needing to
// be closed and reopened by hand.

// `.forEach()` is real (`Iterable#forEach`, JDK 8+) and already proven on a Java `List` return
// elsewhere in this codebase; `.map()`/`.some()` are JS-Array-specific and have no such proven
// precedent on a *Java*-returned List here, so results are built by hand with a plain JS array
// instead of risking either on `KubeUIActions.guildMembers`/`.partyMembers`'s own return values.
function guildHubData(player) {
    let guildId = KubeUIActions.guildOf(player)
    if (guildId === '') {
        return { inGuild: false }
    }
    let selfId = player.uuid.toString()
    let members = []
    let canManage = false
    KubeUIActions.guildMembers(guildId).forEach(m => {
        let memberId = m.getStringOr('id', '')
        let role = m.getStringOr('role', 'member')
        let online = player.level.getServer().getPlayerList().getPlayer(UUID.fromString(memberId))
        members.push({ id: memberId, role: role, name: online ? online.username : memberId })
        if (memberId === selfId && (role === 'owner' || role === 'officer')) {
            canManage = true
        }
    })
    return {
        inGuild: true,
        guildId: guildId,
        guildName: KubeUIActions.guildNameOf(player),
        level: KubeUIActions.guildLevel(guildId),
        canManage: canManage,
        members: members
    }
}

function partyHubData(player) {
    let members = []
    KubeUIActions.partyMembers(player).forEach(id => {
        let online = player.level.getServer().getPlayerList().getPlayer(id)
        members.push({ id: id.toString(), name: online ? online.username : id.toString() })
    })
    return {
        inParty: members.length > 0,
        members: members,
        sharedXp: KubeUIActions.partySharesXp(player),
        sharedLoot: KubeUIActions.partySharesLoot(player)
    }
}

function homeHubData(player) {
    // Wrapped as `{name: ...}` objects rather than a bare list of strings - a list of real
    // `CompoundTag`s has an already-proven safe read path from JS (`.getStringOr('name', '')`
    // per entry, same as the guild-members/claims lists above); a raw `ListTag` of `StringTag`
    // elements has no such proven precedent in this codebase, and a `StringTag`'s own `toString()`
    // includes SNBT quoting rather than the bare value.
    let homes = []
    KubeUIActions.homeNames(player).forEach(name => homes.push({ name: name }))
    return {
        homes: homes,
        cooldownActive: KubeUIActions.isCooldownActive(player, 'kubeui:home_tp'),
        cooldownSeconds: Math.ceil(KubeUIActions.cooldownRemaining(player, 'kubeui:home_tp') / 20)
    }
}

function claimsHubData(player) {
    return { claims: KubeUIActions.claimsOf(player) }
}

function openGuildHub(player) {
    KubeUIActions.openRemote(player, 'kubeui:guild_hub', guildHubData(player))
}

function openPartyHub(player) {
    KubeUIActions.openRemote(player, 'kubeui:party_hub', partyHubData(player))
}

function openHomeHub(player) {
    KubeUIActions.openRemote(player, 'kubeui:home_hub', homeHubData(player))
}

function openClaimsHub(player) {
    KubeUIActions.openRemote(player, 'kubeui:claims_hub', claimsHubData(player))
}

// ---------------------------------------------------------------- GUI button actions
// Reachable only from the default screens above (or a script's own replacement - see
// kubeui_default_screens.js's own doc comment for how to override one) via screen.runServerAction -
// these are NOT slash commands, so building them with KubeUIActions.register(...) (already the
// established, real, JS-only mechanism every other GUI button action in this mod uses) doesn't
// conflict with "toutes les commandes doivent être en JS" - it already is.

KubeUIActions.register('kubeui:guild_gui_create', (player, data) => {
    KubeUIActions.createGuild(player, data.getStringOr('id', ''), data.getStringOr('name', ''))
    openGuildHub(player)
})

KubeUIActions.register('kubeui:guild_gui_rename', (player, data) => {
    let guildId = KubeUIActions.guildOf(player)
    if (guildId !== '') {
        KubeUIActions.renameGuild(player, guildId, data.getStringOr('name', ''))
    }
    openGuildHub(player)
})

KubeUIActions.register('kubeui:guild_gui_invite', (player, data) => {
    let guildId = KubeUIActions.guildOf(player)
    let target = player.level.getServer().getPlayerList().getPlayer(data.getStringOr('name', ''))
    if (guildId !== '' && target) {
        KubeUIActions.inviteToGuild(player, guildId, target)
        target.tell('§d' + KubeUIActions.guildNameOf(player) + '§r added you to their guild.')
    }
    openGuildHub(player)
})

KubeUIActions.register('kubeui:guild_gui_kick', (player, data) => {
    let guildId = KubeUIActions.guildOf(player)
    if (guildId !== '') {
        KubeUIActions.kickFromGuild(player, guildId, UUID.fromString(data.getStringOr('id', '')))
    }
    openGuildHub(player)
})

KubeUIActions.register('kubeui:party_gui_create', (player, data) => {
    KubeUIActions.createParty(player)
    openPartyHub(player)
})

KubeUIActions.register('kubeui:party_gui_invite', (player, data) => {
    let target = player.level.getServer().getPlayerList().getPlayer(data.getStringOr('name', ''))
    if (target) {
        KubeUIActions.inviteToParty(player, target)
        target.tell('§b' + player.username + '§r added you to their party.')
    }
    openPartyHub(player)
})

KubeUIActions.register('kubeui:party_gui_leave', (player, data) => {
    KubeUIActions.leaveParty(player)
    openPartyHub(player)
})

KubeUIActions.register('kubeui:home_gui_set', (player, data) => {
    let name = data.getStringOr('name', '')
    if (name !== '') {
        KubeUIActions.setHome(player, name)
    }
    openHomeHub(player)
})

KubeUIActions.register('kubeui:home_gui_remove', (player, data) => {
    KubeUIActions.removeHome(player, data.getStringOr('name', ''))
    openHomeHub(player)
})

KubeUIActions.register('kubeui:home_gui_tp', (player, data) => {
    let name = data.getStringOr('name', '')
    if (!KubeUIActions.isCooldownActive(player, 'kubeui:home_tp') && KubeUIActions.teleportHome(player, name)) {
        KubeUIActions.startCooldown(player, 'kubeui:home_tp', HOME_TP_COOLDOWN_TICKS)
    }
    openHomeHub(player)
})

ServerEvents.commandRegistry(event => {
    const Commands = event.getCommands()
    const Arguments = event.getArguments()

    // ---------------------------------------------------------------- /guild
    event.register(
        Commands.literal('guild')
            .executes(ctx => {
                openGuildHub(ctx.getSource().getPlayerOrException())
                return 1
            })
            .then(Commands.literal('create')
                .then(Commands.argument('id', Arguments.WORD.create(event))
                    .then(Commands.argument('name', Arguments.GREEDY_STRING.create(event))
                        .executes(ctx => {
                            let player = ctx.getSource().getPlayerOrException()
                            let id = Arguments.WORD.getResult(ctx, 'id')
                            let name = Arguments.GREEDY_STRING.getResult(ctx, 'name')
                            if (!KubeUIActions.createGuild(player, id, name)) {
                                ctx.getSource().sendSystemMessage(Text.of('Could not create guild "' + id + '" - the id is taken, or you\'re already in a guild.').red())
                                return 0
                            }
                            ctx.getSource().sendSystemMessage(Text.of('Guild "' + name + '" created.').green())
                            return 1
                        }))))
            .then(Commands.literal('rename')
                .then(Commands.argument('name', Arguments.GREEDY_STRING.create(event))
                    .executes(ctx => {
                        let player = ctx.getSource().getPlayerOrException()
                        let guildId = KubeUIActions.guildOf(player)
                        let name = Arguments.GREEDY_STRING.getResult(ctx, 'name')
                        if (guildId === '' || !KubeUIActions.renameGuild(player, guildId, name)) {
                            ctx.getSource().sendSystemMessage(Text.of('You need to be the owner of a guild to rename it.').red())
                            return 0
                        }
                        ctx.getSource().sendSystemMessage(Text.of('Your guild is now named "' + name + '".').green())
                        return 1
                    })))
            .then(Commands.literal('invite')
                .then(Commands.argument('target', Arguments.PLAYER.create(event))
                    .executes(ctx => {
                        let player = ctx.getSource().getPlayerOrException()
                        let target = Arguments.PLAYER.getResult(ctx, 'target')
                        let guildId = KubeUIActions.guildOf(player)
                        if (guildId === '' || !KubeUIActions.inviteToGuild(player, guildId, target)) {
                            ctx.getSource().sendSystemMessage(Text.of('Could not invite ' + target.username + ' - you need officer rank, and they must not already be in a guild.').red())
                            return 0
                        }
                        target.tell('§d' + KubeUIActions.guildNameOf(player) + '§r added you to their guild.')
                        ctx.getSource().sendSystemMessage(Text.of('Invited ' + target.username + '.').green())
                        return 1
                    })))
            .then(Commands.literal('kick')
                .then(Commands.argument('target', Arguments.PLAYER.create(event))
                    .executes(ctx => {
                        let player = ctx.getSource().getPlayerOrException()
                        let target = Arguments.PLAYER.getResult(ctx, 'target')
                        let guildId = KubeUIActions.guildOf(player)
                        if (guildId === '' || !KubeUIActions.kickFromGuild(player, guildId, target.uuid)) {
                            ctx.getSource().sendSystemMessage(Text.of('Could not kick ' + target.username + ' - you need officer rank, and they must not be the owner.').red())
                            return 0
                        }
                        ctx.getSource().sendSystemMessage(Text.of('Kicked ' + target.username + '.').green())
                        return 1
                    })))
            .then(Commands.literal('info')
                .executes(ctx => {
                    let player = ctx.getSource().getPlayerOrException()
                    let guildId = KubeUIActions.guildOf(player)
                    if (guildId === '') {
                        ctx.getSource().sendSystemMessage(Text.of('You\'re not in a guild.').red())
                        return 0
                    }
                    let members = KubeUIActions.guildMembers(guildId)
                    ctx.getSource().sendSystemMessage(Text.of('§d' + KubeUIActions.guildNameOf(player) + '§r (level ' + KubeUIActions.guildLevel(guildId) + ', ' + members.length + ' member(s))'))
                    members.forEach(m => {
                        let memberId = m.getStringOr('id', '')
                        let online = ctx.getSource().getServer().getPlayerList().getPlayer(UUID.fromString(memberId))
                        let displayName = online ? online.username : memberId
                        ctx.getSource().sendSystemMessage(Text.of('  §7- ' + displayName + ' (' + m.getStringOr('role', 'member') + ')'))
                    })
                    return 1
                }))
    )

    // ---------------------------------------------------------------- /party
    event.register(
        Commands.literal('party')
            .executes(ctx => {
                openPartyHub(ctx.getSource().getPlayerOrException())
                return 1
            })
            .then(Commands.literal('create')
                .executes(ctx => {
                    let player = ctx.getSource().getPlayerOrException()
                    if (!KubeUIActions.createParty(player)) {
                        ctx.getSource().sendSystemMessage(Text.of('You\'re already in a party.').red())
                        return 0
                    }
                    ctx.getSource().sendSystemMessage(Text.of('Party created - invite people with /party invite <player>.').green())
                    return 1
                }))
            .then(Commands.literal('invite')
                .then(Commands.argument('target', Arguments.PLAYER.create(event))
                    .executes(ctx => {
                        let player = ctx.getSource().getPlayerOrException()
                        let target = Arguments.PLAYER.getResult(ctx, 'target')
                        if (!KubeUIActions.inviteToParty(player, target)) {
                            ctx.getSource().sendSystemMessage(Text.of('Could not invite ' + target.username + ' - you need to be the party leader, and they must not already be in a party.').red())
                            return 0
                        }
                        target.tell('§b' + player.username + '§r added you to their party.')
                        ctx.getSource().sendSystemMessage(Text.of('Invited ' + target.username + '.').green())
                        return 1
                    })))
            .then(Commands.literal('leave')
                .executes(ctx => {
                    let player = ctx.getSource().getPlayerOrException()
                    KubeUIActions.leaveParty(player)
                    ctx.getSource().sendSystemMessage(Text.of('Left your party.').green())
                    return 1
                }))
            .then(Commands.literal('list')
                .executes(ctx => {
                    let player = ctx.getSource().getPlayerOrException()
                    let members = KubeUIActions.partyMembers(player)
                    if (members.length === 0) {
                        ctx.getSource().sendSystemMessage(Text.of('You\'re not in a party.').red())
                        return 0
                    }
                    ctx.getSource().sendSystemMessage(Text.of('Party (' + members.length + ' member(s)):'))
                    members.forEach(id => {
                        let online = ctx.getSource().getServer().getPlayerList().getPlayer(id)
                        ctx.getSource().sendSystemMessage(Text.of('  §7- ' + (online ? online.username : id)))
                    })
                    return 1
                }))
    )

    // ---------------------------------------------------------------- /claims
    event.register(
        Commands.literal('claims')
            .executes(ctx => {
                openClaimsHub(ctx.getSource().getPlayerOrException())
                return 1
            })
    )

    // ---------------------------------------------------------------- /home
    event.register(
        Commands.literal('home')
            .executes(ctx => {
                openHomeHub(ctx.getSource().getPlayerOrException())
                return 1
            })
            .then(Commands.literal('set')
                .then(Commands.argument('name', Arguments.WORD.create(event))
                    .executes(ctx => {
                        let player = ctx.getSource().getPlayerOrException()
                        let name = Arguments.WORD.getResult(ctx, 'name')
                        KubeUIActions.setHome(player, name)
                        ctx.getSource().sendSystemMessage(Text.of('Home "' + name + '" set.').green())
                        return 1
                    })))
            .then(Commands.literal('remove')
                .then(Commands.argument('name', Arguments.WORD.create(event))
                    .executes(ctx => {
                        let player = ctx.getSource().getPlayerOrException()
                        let name = Arguments.WORD.getResult(ctx, 'name')
                        if (!KubeUIActions.removeHome(player, name)) {
                            ctx.getSource().sendSystemMessage(Text.of('No home named "' + name + '".').red())
                            return 0
                        }
                        ctx.getSource().sendSystemMessage(Text.of('Home "' + name + '" removed.').green())
                        return 1
                    })))
            .then(Commands.literal('list')
                .executes(ctx => {
                    let player = ctx.getSource().getPlayerOrException()
                    let names = KubeUIActions.homeNames(player)
                    if (names.length === 0) {
                        ctx.getSource().sendSystemMessage(Text.of('You have no homes set - "/home set <name>" to add one.').red())
                        return 0
                    }
                    ctx.getSource().sendSystemMessage(Text.of('Your homes: ' + names.join(', ')))
                    return 1
                }))
            .then(Commands.literal('tp')
                .then(Commands.argument('name', Arguments.WORD.create(event))
                    .executes(ctx => {
                        let player = ctx.getSource().getPlayerOrException()
                        let name = Arguments.WORD.getResult(ctx, 'name')
                        if (KubeUIActions.isCooldownActive(player, 'kubeui:home_tp')) {
                            let seconds = Math.ceil(KubeUIActions.cooldownRemaining(player, 'kubeui:home_tp') / 20)
                            ctx.getSource().sendSystemMessage(Text.of('You need to wait ' + seconds + 's before teleporting home again.').red())
                            return 0
                        }
                        if (!KubeUIActions.teleportHome(player, name)) {
                            ctx.getSource().sendSystemMessage(Text.of('No home named "' + name + '".').red())
                            return 0
                        }
                        KubeUIActions.startCooldown(player, 'kubeui:home_tp', HOME_TP_COOLDOWN_TICKS)
                        ctx.getSource().sendSystemMessage(Text.of('Teleported to "' + name + '".').green())
                        return 1
                    })))
    )

    // ---------------------------------------------------------------- /social
    // Opens the tabbed Général/Teams/Party screen (see kubeui_social_hub_demo.js, client_scripts) -
    // "sa ouvre un onglet" (real ask). The server has no opinion on what that screen looks like, it
    // just tells the client to open whatever it registered for this screen id, same real
    // KubeUIActions.openRemote/KubeUIRemoteScreens.register round trip every other bridge screen in
    // this mod already uses.
    event.register(
        Commands.literal('social')
            .executes(ctx => {
                let player = ctx.getSource().getPlayerOrException()
                KubeUIActions.openRemote(player, 'kubeui:social_hub', {
                    guildId: KubeUIActions.guildOf(player),
                    guildName: KubeUIActions.guildNameOf(player),
                    partySize: KubeUIActions.partyMembers(player).length
                })
                return 1
            })
    )
})
