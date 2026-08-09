// Reference example for the housing/claims system - claim land, set permissions, invite a
// visitor, rent it out, merge/split, named homes, and an intrusion alert - built with
// KubeUIActions.claimLand/.setClaimPermission/.inviteClaimVisit/.rentClaim/.setHome.

KubeUIActions.registerCurrency('gold')
KubeUIActions.setClaimLimits(3, 10000) // 3 claims per player, 10 000 blocks (e.g. 100x100) each

KubeUIActions.register('kubeui_demo:housing_claim', (player, data) => {
    let x = Math.floor(player.x)
    let z = Math.floor(player.z)
    let claimId = 'kubeui_demo:home_' + player.uuid
    if (KubeUIActions.claimLand(player, claimId, x - 15, 0, z - 15, x + 15, 255, z + 15, player.level.dimension.toString())) {
        KubeUIActions.setClaimPermission(player, claimId, 'break', false)
        KubeUIActions.setClaimPermission(player, claimId, 'place', false)
        player.tell('§a30x30 claim made around you ("' + claimId + '") - break/place set to owner-only.')
        player.tell('§7A non-member walking in will toast you.')
    } else {
        player.tell('§cCouldn\'t claim (limit reached, area too big, or id taken).')
    }
})

KubeUIActions.register('kubeui_demo:housing_invite_note', (player, data) => {
    let claimId = 'kubeui_demo:home_' + player.uuid
    player.tell('§7With a real target player: KubeUIActions.inviteClaimVisit(you, "' + claimId + '", them, 1200) (1 minute)')
    player.tell('§7Or KubeUIActions.rentClaim(you, "' + claimId + '", them, "gold", 10, 6000) (10 gold, 5 minutes)')
})

KubeUIActions.register('kubeui_demo:housing_set_home', (player, data) => {
    KubeUIActions.setHome(player, 'base')
    player.tell('§aHome "base" set at your current position.')
})

KubeUIActions.register('kubeui_demo:housing_list_homes', (player, data) => {
    let names = KubeUIActions.homeNames(player)
    if (names.length === 0) {
        player.tell('§7No homes set yet.')
        return
    }
    player.tell('§bYour homes: §7' + names.join(', '))
})

KubeUIActions.register('kubeui_demo:housing_go_home', (player, data) => {
    if (KubeUIActions.teleportHome(player, 'base')) {
        player.tell('§aTeleported to "base".')
    } else {
        player.tell('§cNo home named "base" - set one first.')
    }
})

// A real, OP-gated snapshot of every major KubeUI system - empty for a non-operator, since
// KubeUIActions.serverDashboard(...) checks the real permission level itself, server-side.
KubeUIActions.register('kubeui_demo:server_dashboard', (player, data) => {
    let stats = KubeUIActions.serverDashboard(player)
    if (!stats.contains('currencies')) {
        player.tell('§cYou need operator permissions to see the server dashboard.')
        return
    }
    player.tell('§b--- KubeUI Server Dashboard ---')
    player.tell('§7Currencies: §f' + stats.getIntOr('currencies', 0))
    player.tell('§7Guilds: §f' + stats.getIntOr('guilds', 0))
    player.tell('§7Claims: §f' + stats.getIntOr('claims', 0))
    player.tell('§7Dungeons defined: §f' + stats.getIntOr('dungeons', 0))
    player.tell('§7Quests defined: §f' + stats.getIntOr('quests', 0))
    player.tell('§7Online players: §f' + stats.getIntOr('onlinePlayers', 0))
})
