// Reference example for the friends/party/presence/chat social systems - built with
// KubeUIActions.addFriend/.createParty/.requestTeleport/.presenceOf/.sendPartyChat/.sendLocalChat.

KubeUIActions.register('kubeui_demo:social_create_party', (player, data) => {
    if (KubeUIActions.createParty(player)) {
        player.tell('§aParty created - you\'re the leader. Invite a real player with')
        player.tell('§7KubeUIActions.inviteToParty(you, them) once one is online.')
    } else {
        player.tell('§cYou\'re already in a party.')
    }
})

KubeUIActions.register('kubeui_demo:social_leave_party', (player, data) => {
    KubeUIActions.leaveParty(player)
    player.tell('§7Left/disbanded your party.')
})

KubeUIActions.register('kubeui_demo:social_share_toggle', (player, data) => {
    let sharedXp = data.getBooleanOr('xp', true)
    let sharedLoot = data.getBooleanOr('loot', true)
    KubeUIActions.setPartySharing(player, sharedXp, sharedLoot)
    player.tell('§7Party sharing set - XP: ' + sharedXp + ', Loot: ' + sharedLoot + ' (only takes effect if you\'re the leader).')
})

KubeUIActions.register('kubeui_demo:social_party_chat', (player, data) => {
    KubeUIActions.sendPartyChat(player, data.getStringOr('message', 'Hello, party!'))
})

KubeUIActions.register('kubeui_demo:social_local_chat', (player, data) => {
    KubeUIActions.sendLocalChat(player, data.getStringOr('message', 'Hello, nearby!'), 20)
})

KubeUIActions.register('kubeui_demo:social_presence', (player, data) => {
    player.tell('§7Your current presence: §f' + KubeUIActions.presenceOf(player))
    player.tell('§7(fight something with the Combat Demo to see it change to "in_combat")')
})

KubeUIActions.register('kubeui_demo:social_toggle_friend_notify', (player, data) => {
    let enabled = data.getBooleanOr('enabled', true)
    KubeUIActions.setFriendActivityNotifications(player, enabled)
    player.tell(enabled ? '§aYou\'ll be toasted when a friend logs in.' : '§7Friend login toasts disabled.')
})

KubeUIActions.register('kubeui_demo:social_request_tp', (player, data) => {
    // A real request needs a second real player (KubeUIActions.requestTeleport(from, to)) -
    // demonstrated as a note here for the same reason the guild demo notes guild invites.
    player.tell('§7With a real target online: KubeUIActions.requestTeleport(you, them), then they')
    player.tell('§7run KubeUIActions.acceptTeleport(them) (or .denyTeleport) to resolve it.')
})

// Reuses the already-real proximity broadcast (KubeUIActions.sendLocalChat) rather than a second,
// separate distance-check implementation just for emotes.
KubeUIActions.register('kubeui_demo:social_emote', (player, data) => {
    let emote = data.getStringOr('emote', 'waves')
    KubeUIActions.sendLocalChat(player, '* ' + player.username + ' ' + emote + ' *', 20)
})
