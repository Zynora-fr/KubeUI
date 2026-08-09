// Client-side half of the social reference example (see kubeui_social_demo.js in server_scripts).
// Block list/contact notes are purely local (KubeUI.blockPlayer/.setContactNote) - never sent to
// the server at all, so there's nothing for that half to do.

function openEmoteWheel() {
    let emotes = ['waves', 'dances', 'laughs', 'cries', 'salutes', 'facepalms']
    let centerX = 110, centerY = 110, radius = 75

    let builder = KubeUI.builder('Emote Wheel').elementSize(80, 20)
    emotes.forEach((emote, i) => {
        let angle = (2 * Math.PI * i) / emotes.length
        let x = Math.round(centerX + Math.cos(angle) * radius) - 40
        let y = Math.round(centerY + Math.sin(angle) * radius) - 10
        builder.button(emote, screen => {
            screen.runServerAction('kubeui_demo:social_emote', { emote: emote })
            screen.close()
        }).absolute(x, y)
    })
    builder.button('Close', screen => screen.close()).absolute(centerX - 30, centerY - 10)
    builder.open()
}

function openSocialDemo() {
    KubeUI.builder('Social Demo')
        .draggable()
        .tab('Party & Friends', tab => tab
            .elementSize(280, 20)
            .button('Create Party', screen => screen.runServerAction('kubeui_demo:social_create_party', null))
            .button('Leave/Disband Party', screen => screen.runServerAction('kubeui_demo:social_leave_party', null))
            .toggle('shareXp', 'Share XP with party (as leader)', true, (screen, value) => {
                screen.runServerAction('kubeui_demo:social_share_toggle', { xp: value, loot: true })
            })
            .divider()
            .button('Request Teleport (see chat for how)', screen => screen.runServerAction('kubeui_demo:social_request_tp', null))
            .button('My Presence', screen => screen.runServerAction('kubeui_demo:social_presence', null))
            .toggle('friendNotify', 'Toast me when a friend logs in', false, (screen, value) => {
                screen.runServerAction('kubeui_demo:social_toggle_friend_notify', { enabled: value })
            })
            .button('Open Emote Wheel', screen => {
                screen.close()
                openEmoteWheel()
            })
        )
        .tab('Chat Channels', tab => tab
            .elementSize(280, 20)
            .label('info', 'Real channels: Party (members only), Local (20 blocks), Guild (see Guild Demo).')
            .textField('partyMsg', '', 'Party message...', (screen, value) => {})
            .button('Send to Party', screen => screen.runServerAction('kubeui_demo:social_party_chat', { message: screen.getTextFieldValue('partyMsg') || 'Hello, party!' }))
            .divider()
            .textField('localMsg', '', 'Local message...', (screen, value) => {})
            .button('Send Locally (20 blocks)', screen => screen.runServerAction('kubeui_demo:social_local_chat', { message: screen.getTextFieldValue('localMsg') || 'Hello, nearby!' }))
        )
        .tab('Block List & Notes', tab => tab
            .elementSize(280, 20)
            .label('info2', 'Purely local - never sent to the server or shown to anyone else.')
            .label('blockInfo', 'Block/note a real player by UUID from a script:')
            .label('blockExample', 'KubeUI.blockPlayer(uuid) / .setContactNote(uuid, name, "note")')
            .label('blockExample2', 'KubeUI.isPlayerBlocked(uuid) / .contactNote(uuid) to read them back.')
            .divider()
            .button('Close', screen => screen.close())
        )
        .open()
}
