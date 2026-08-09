// The client half of `/social` (see kubeui_guild_party_housing_commands.js, server_scripts) - a
// tabbed Général/Teams/Party screen. Tabs are registered through KubeUISocialHub.registerTab(...)
// rather than hardcoded into the screen-building function below, so "tout ça est configurable en
// JS" (real ask): any script - including a third-party modpack script, not just this one - can call
// KubeUISocialHub.registerTab(...) again to add its own tab (a "Guild Wars" tab, a "Trade" tab, ...),
// and it shows up here automatically, with no changes to this file or any Java needed either way.

// The last snapshot the server sent (see the `/social` command, server-side - it hands over the
// caller's current guild/party status each time) - a plain top-level JS object each registered
// tab's own onOpen callback closes over, not a field on KubeUISocialHub itself (a real Java class
// with no such property - per-open-screen state like this belongs to the script, not the registry).
const KubeUISocialHubState = { guildId: '', guildName: '', partySize: 0 }

KubeUISocialHub.registerTab('kubeui:general', 'Général', 0, tab => {
    tab.label('title', 'Général')
        .label('hint1', 'Commands: /guild, /party, /home, /claims')
        .divider()
        .label('hint2', '§7Type in chat as usual - if you\'re in a guild, your tag shows automatically.')
})

KubeUISocialHub.registerTab('kubeui:teams', 'Teams', 10, tab => {
    tab.label('title', 'Your Guild')
    if (KubeUISocialHubState.guildName) {
        tab.label('name', '§d' + KubeUISocialHubState.guildName + '§r (' + KubeUISocialHubState.guildId + ')')
            .label('hint', '§7/guild info · /guild invite <player> · /guild kick <player> · /guild rename <name>')
    } else {
        tab.label('none', 'You\'re not in a guild yet.')
            .label('hint', '§7/guild create <id> <name>')
    }
})

KubeUISocialHub.registerTab('kubeui:party', 'Party', 20, tab => {
    tab.label('title', 'Your Party')
    if (KubeUISocialHubState.partySize > 0) {
        tab.label('size', KubeUISocialHubState.partySize + ' member(s)')
            .label('hint', '§7/party list · /party invite <player> · /party leave')
    } else {
        tab.label('none', 'You\'re not in a party yet.')
            .label('hint', '§7/party create')
    }
})

KubeUIRemoteScreens.register('kubeui:social_hub', data => {
    KubeUISocialHubState.guildId = data.getStringOr('guildId', '')
    KubeUISocialHubState.guildName = data.getStringOr('guildName', '')
    KubeUISocialHubState.partySize = data.getIntOr('partySize', 0)

    let builder = KubeUI.builder('Social').elementSize(240, 20)
    // Every registered tab, lowest `order` first (KubeUISocialHub.Tab is a real Java record - its
    // accessors are the plain component names, .name()/.id()/.onOpen(), not JavaBean-style getters).
    KubeUISocialHub.tabs().forEach(t => {
        builder.tab(t.name(), children => {
            t.onOpen().accept(children)
            children.divider().button('Close', screen => screen.close())
        })
    })
    builder.open()
})
