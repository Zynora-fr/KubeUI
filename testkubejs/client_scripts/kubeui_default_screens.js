// Real GUIs for /guild, /party, /home and /claims (bare, no subcommand) - "fait en sorte d'avoir
// un beau GUI... on voit tout dans le GUI" (real ask): creating a guild, inviting/kicking members,
// managing a party, and setting/removing/teleporting to homes all happen through these screens
// instead of chat-only command output (the subcommands - "/guild create <id> <name>", etc. - still
// exist too, for scripting/chat-only play).
//
// TO REPLACE ANY OF THESE with your own screen ("si on ne veut pas celui de base on peut en
// mettre un autre dans les scripts JS" - real ask): call `KubeUIRemoteScreens.register(screenId,
// yourOwnHandler)` again with the same screen id, from a script that loads after this one -
// `KubeUIRemoteScreens.register` *replaces* whatever was registered for that id (real, already-
// established behavior - it's a plain `Map#put`, not additive), so your own handler simply wins.
// The four ids: `kubeui:guild_hub`, `kubeui:party_hub`, `kubeui:home_hub`, `kubeui:claims_hub` -
// each receives the same real `data` `CompoundTag` documented next to its handler below, so a
// replacement screen has everything the default one has to work with.

// `data`: `inGuild` (bool); if true also `guildId`/`guildName`/`level`/`canManage` (bool - true for
// the owner/an officer) and `members` (a list of `{id, role, name}`).
KubeUIRemoteScreens.register('kubeui:guild_hub', data => {
    let builder = KubeUI.builder('Guild').elementSize(260, 20)

    if (!data.getBooleanOr('inGuild', false)) {
        let newId = ''
        let newName = ''
        builder.label('title', 'You\'re not in a guild yet.')
            .textField('id', '', 'Guild id (e.g. ironclad)', (ctx, v) => newId = v)
            .textField('name', '', 'Guild display name', (ctx, v) => newName = v)
            .button('Create Guild', ctx => ctx.runServerAction('kubeui:guild_gui_create', { id: newId, name: newName }))
    } else {
        let guildName = data.getStringOr('guildName', '')
        let canManage = data.getBooleanOr('canManage', false)
        builder.label('title', '§d' + guildName + '§r - level ' + data.getIntOr('level', 0))
            .divider()

        if (canManage) {
            let renameTo = guildName
            let inviteName = ''
            builder.textField('rename', guildName, 'New name', (ctx, v) => renameTo = v)
                .button('Rename', ctx => ctx.runServerAction('kubeui:guild_gui_rename', { name: renameTo }))
                .divider()
                .textField('invite', '', 'Player to invite', (ctx, v) => inviteName = v)
                .button('Invite', ctx => ctx.runServerAction('kubeui:guild_gui_invite', { name: inviteName }))
                .divider()
        }

        let members = data.getListOrEmpty('members')
        builder.label('membersTitle', 'Members (' + members.length + '):')
        members.forEach(m => {
            let name = m.getStringOr('name', '?')
            let role = m.getStringOr('role', 'member')
            builder.row(row => {
                // Explicit widths - a row's children default to the *full* elementWidth each
                // (260 here) unless told otherwise, so a bare label+button pair would overflow
                // the row way past the screen's actual width (a real, reported bug: the label
                // ended up pushed off-screen entirely and the button clipped at the edge).
                row.label('member_' + m.getStringOr('id', ''), name + ' §7(' + role + ')').width(190)
                if (canManage && role !== 'owner') {
                    row.button('Kick', ctx => ctx.runServerAction('kubeui:guild_gui_kick', { id: m.getStringOr('id', '') })).width(60)
                }
            })
        })
    }

    builder.divider().button('Close', ctx => ctx.close()).open()
})

// `data`: `inParty` (bool), `members` (a list of `{id, name}`), `sharedXp`/`sharedLoot` (bool).
KubeUIRemoteScreens.register('kubeui:party_hub', data => {
    let builder = KubeUI.builder('Party').elementSize(260, 20)

    if (!data.getBooleanOr('inParty', false)) {
        builder.label('title', 'You\'re not in a party yet.')
            .button('Create Party', ctx => ctx.runServerAction('kubeui:party_gui_create', {}))
    } else {
        let inviteName = ''
        builder.label('title', 'Your party')
            .divider()
            .textField('invite', '', 'Player to invite', (ctx, v) => inviteName = v)
            .button('Invite', ctx => ctx.runServerAction('kubeui:party_gui_invite', { name: inviteName }))
            .divider()

        let members = data.getListOrEmpty('members')
        builder.label('membersTitle', 'Members (' + members.length + '):')
        members.forEach(m => builder.label('member_' + m.getStringOr('id', ''), '§7- ' + m.getStringOr('name', '?')))

        builder.divider()
            .label('sharing', 'Shared XP: ' + (data.getBooleanOr('sharedXp', false) ? '§aon' : '§7off')
                + '  §rShared loot: ' + (data.getBooleanOr('sharedLoot', false) ? '§aon' : '§7off'))
            .button('Leave Party', ctx => ctx.runServerAction('kubeui:party_gui_leave', {}))
    }

    builder.divider().button('Close', ctx => ctx.close()).open()
})

// `data`: `homes` (a list of `{name}`), `cooldownActive` (bool), `cooldownSeconds` (int, only
// meaningful while `cooldownActive`).
KubeUIRemoteScreens.register('kubeui:home_hub', data => {
    let builder = KubeUI.builder('Homes').elementSize(260, 20)
    let homes = data.getListOrEmpty('homes')

    if (homes.length === 0) {
        builder.label('none', 'You have no homes set yet.')
    } else {
        builder.label('title', 'Your homes (' + homes.length + '):')
        homes.forEach(h => {
            let homeName = h.getStringOr('name', '')
            // Explicit widths - see the same fix's own comment in the guild members row above
            // (a row's children default to the full elementWidth each, which overflowed the
            // screen with 3 children at 260px apiece - a real, reported bug).
            builder.row(row => row
                .label('home_' + homeName, homeName).width(150)
                .button('TP', ctx => ctx.runServerAction('kubeui:home_gui_tp', { name: homeName })).width(45)
                .button('Remove', ctx => ctx.runServerAction('kubeui:home_gui_remove', { name: homeName })).width(60))
        })
        if (data.getBooleanOr('cooldownActive', false)) {
            builder.label('cooldown', '§7Teleport cooldown: ' + data.getIntOr('cooldownSeconds', 0) + 's left')
        }
    }

    let newHomeName = ''
    builder.divider()
        .textField('newHome', '', 'New home name', (ctx, v) => newHomeName = v)
        .button('Set Home Here', ctx => ctx.runServerAction('kubeui:home_gui_set', { name: newHomeName }))
        .divider()
        .button('Close', ctx => ctx.close())
        .open()
})

// `data`: `claims` (a list of `{id, x1, y1, z1, x2, y2, z2, dimension, sizeBlocks}` - see
// KubeUIActions.claimsOf).
KubeUIRemoteScreens.register('kubeui:claims_hub', data => {
    let builder = KubeUI.builder('Claims').elementSize(260, 20)
    let claims = data.getListOrEmpty('claims')

    if (claims.length === 0) {
        builder.label('none', 'You don\'t own any claims.')
    } else {
        builder.label('title', 'Your claims (' + claims.length + '):')
        claims.forEach(c => builder.label('claim_' + c.getStringOr('id', ''),
            c.getStringOr('id', '?') + ' §7- ' + Math.round(c.getDoubleOr('sizeBlocks', 0)) + ' blocks, ' + c.getStringOr('dimension', '?')))
    }

    builder.divider().button('Close', ctx => ctx.close()).open()
})
