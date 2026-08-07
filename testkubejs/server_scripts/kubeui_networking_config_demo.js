// Server side of the networking demo (see client_scripts/kubeui_test_networking_config.js) -
// covers throttling, schema validation, per-player server storage, server->client pushed/
// broadcast screens, open-screen tracking, and server-controlled sidebar icon visibility.

KubeUIActions.register('kubeui_demo:throttled_ping', 1000, (player, data) => {
    player.tell('§b[KubeUI] Pong! (throttled server-side to once per second, per player)')
})

KubeUIActions.register('kubeui_demo:schema_validated', {message: 'string', count: 'int'}, (player, data) => {
    player.tell('§aValidated payload received: "' + data.getStringOr('message', '') + '" x' + data.getIntOr('count', 0))
})

KubeUIActions.register('kubeui_demo:remember_favorite_color', (player, data) => {
    let stored = KubeUIActions.playerData(player)
    stored.putInt('favoriteColor', data.getIntOr('color', 0xFFFFFFFF | 0))
    player.tell('§aSaved - still there after a reconnect or a server restart (KubeUIActions.playerData).')
})

KubeUIActions.register('kubeui_demo:recall_favorite_color', (player, data) => {
    let stored = KubeUIActions.playerData(player)
    if (stored.contains('favoriteColor')) {
        player.tell('§bYour saved favorite color: #' + (stored.getIntOr('favoriteColor', 0) >>> 0).toString(16))
    } else {
        player.tell('§7Nothing saved yet - try "Remember my color" first.')
    }
})

KubeUIActions.register('kubeui_demo:push_remote_screen', (player, data) => {
    KubeUIActions.openRemote(player, 'kubeui_demo:remote_screen', {
        message: 'Pushed to just you by the server.'
    })
})

KubeUIActions.register('kubeui_demo:broadcast_to_everyone', (player, data) => {
    KubeUIActions.broadcastUpdate('kubeui_demo:remote_screen', {
        message: player.name.string + ' says hello to the whole server!'
    })
})

KubeUIActions.register('kubeui_demo:check_open_screen', (player, data) => {
    let screenId = KubeUIActions.getOpenScreenId(player)
    player.tell(screenId
        ? ('§bServer sees you have "' + screenId + '" open right now.')
        : '§7Server has no .screenId(...)-tagged screen recorded as open for you.')
})

KubeUIActions.register('kubeui_demo:toggle_admin_icon', (player, data) => {
    let show = data.getBooleanOr('show', true)
    KubeUIActions.setSidebarIconVisible(player, 'kubeui_demo:admin', show)
    player.tell(show ? '§aAdmin sidebar icon shown.' : '§7Admin sidebar icon hidden.')
})
