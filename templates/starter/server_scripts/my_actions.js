// Starter server-side action handler - anything a script needs to trust (a price, a permission, a
// reward) belongs here, not in the client script. See the main README's "Server integration"
// section for why.

KubeUIActions.register('my_addon:say_hi', (player, data) => {
    player.tell('§aHello, ' + player.username + '! This came from server_scripts.')
})
