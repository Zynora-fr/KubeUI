// Client-side half of the guild reference example (see kubeui_guild_demo.js in server_scripts).

function openGuildDemo() {
    KubeUI.builder('Guild Demo')
        .elementSize(280, 20)
        .label('info', 'The Ironclad - a real, disk-persisted guild with roles/alliances/leveling.')
        .button('Create Guild (you become owner)', screen => screen.runServerAction('kubeui_demo:guild_create', null))
        .button('How to Invite a Real Player', screen => screen.runServerAction('kubeui_demo:guild_invite_self_note', null))
        .divider()
        .textField('chatMessage', '', 'Message...', (screen, value) => {})
        .button('Send Guild Chat', screen => {
            let message = screen.getTextFieldValue('chatMessage')
            screen.runServerAction('kubeui_demo:guild_chat', { message: message || 'Hello, guild!' })
        })
        .divider()
        .button('Claim 32x32 Territory Around Me', screen => screen.runServerAction('kubeui_demo:guild_claim_territory', null))
        .button('Add 100 Guild XP', screen => screen.runServerAction('kubeui_demo:guild_add_xp', null))
        .button('Set Badge (Netherite Ingot)', screen => screen.runServerAction('kubeui_demo:guild_set_badge', null))
        .button('Schedule "Weekly Raid" (10s)', screen => screen.runServerAction('kubeui_demo:guild_schedule_event', null))
        .divider()
        .button('Guild Leaderboard', screen => screen.runServerAction('kubeui_demo:guild_leaderboard', null))
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
