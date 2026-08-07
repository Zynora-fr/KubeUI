// Demonstrates KubeUI's advanced server networking (server->client push, throttling, schema
// validation, permission-gated widgets, screen tracking, per-player server storage) and disk
// config/persistence features. Server-side handlers in
// server_scripts/kubeui_networking_config_demo.js. Opened from the test menu (see
// kubeui_test_menu.js), which opens automatically on world join.

// Registers a handler for screens the server pushes via KubeUIActions.openRemote(...)/
// .broadcastUpdate(...) - try the buttons in the "Server -> Client" tab below, or have another
// player on the same world trigger the broadcast one.
KubeUIRemoteScreens.register('kubeui_demo:remote_screen', data => {
    KubeUI.builder('Pushed From The Server')
        .label('msg', data.getStringOr('message', '(no message)'))
        .button('Close', screen => screen.close())
        .open()
})

function openNetworkingConfigDemo() {
    KubeUI.builder('Networking & Config Demo')
        .screenId('kubeui_demo:networking_config_demo') // opts this screen into server-side tracking
        .elementSize(260, 24)
        .tab('Server -> Client', tab => tab
            .label('pushHint', 'Ask the server to push/broadcast a screen to this id via KubeUIRemoteScreens.register(...) above.')
            .button('Push me a screen', screen => screen.runServerAction('kubeui_demo:push_remote_screen', {}))
            .button('Broadcast to everyone', screen => screen.runServerAction('kubeui_demo:broadcast_to_everyone', {}))
            .divider()
            .button('Check what the server thinks is open', screen => screen.runServerAction('kubeui_demo:check_open_screen', {}))
        )
        .tab('Throttle, schema & ack', tab => tab
            .label('throttleHint', 'Click fast - server-side throttle (1/s) silently drops the extra ones.')
            .button('Ping (throttled)', screen => screen.runServerAction('kubeui_demo:throttled_ping', {}))
            .divider()
            .label('schemaHint', 'Sends a payload matching the server\'s schema (message: string, count: int).')
            .button('Send valid payload', screen => screen.runServerAction('kubeui_demo:schema_validated', {message: 'hello', count: 3}))
            .divider()
            .label('ackHint', 'Uses the onAck callback to confirm the server actually processed it.')
            .button('Ping with acknowledgment', screen => {
                screen.runServerAction('kubeui_demo:throttled_ping', {}, (ctxScreen, success) => {
                    screen.setLabel('ackStatus', success ? 'Acknowledged!' : 'Rejected (throttled?)')
                })
            })
            .label('ackStatus', 'Acknowledged: (nothing sent yet)')
        )
        .tab('Per-player storage', tab => tab
            .label('storageHint', 'Persists across reconnects and server restarts (KubeUIActions.playerData).')
            .colorPicker('favColor', 0xFFFF5555, () => {})
            .button('Remember my color', screen => {
                screen.runServerAction('kubeui_demo:remember_favorite_color', {color: screen.getColor('favColor')})
            })
            .button('Recall my color', screen => screen.runServerAction('kubeui_demo:recall_favorite_color', {}))
        )
        .tab('Permission-gated', tab => tab
            .label('permHint', 'Greyed out until the server confirms via the real PermissionAPI - ops only by default.')
            .button('Admin-only action', screen => {})
                .requirePermission('kubeui_demo.admin_button')
            .divider()
            .button('Show admin sidebar icon', screen => screen.runServerAction('kubeui_demo:toggle_admin_icon', {show: true}))
                .requirePermission('kubeui_demo.admin_button')
            .button('Hide admin sidebar icon', screen => screen.runServerAction('kubeui_demo:toggle_admin_icon', {show: false}))
                .requirePermission('kubeui_demo.admin_button')
        )
        .tab('Config screen', tab => tab
            .label('configHint', 'Built entirely from a data schema - see KubeUI.configScreen(...) below.')
        )
        .button('Open auto-generated config screen', () => openAutoConfigScreen())
        .button('Close', screen => screen.close())
        .open()
}

function openAutoConfigScreen() {
    KubeUI.configScreen('Auto-Generated Config', [
        {id: 'heading', type: 'label', label: '--- Demo settings ---'},
        {id: 'volume', type: 'slider', label: 'Volume', min: 0, max: 100, initial: 50},
        {id: 'nickname', type: 'text', label: 'Nickname', initial: ''},
        {id: 'notifications', type: 'toggle', label: 'Enable notifications', initial: true},
        {id: 'difficulty', type: 'dropdown', label: 'Difficulty', options: ['Easy', 'Normal', 'Hard'], initial: 'Normal'},
        {id: 'lives', type: 'number', label: 'Lives', min: 1, max: 9, initial: 3},
    ], (screen, fieldId, value) => {
        console.log('Config field changed: ' + fieldId + ' = ' + value)
    })
        .button('Close', screen => screen.close())
        .open()
}

// KubeUISidebar.addItem('kubeui_demo:admin', 'minecraft:command_block', 'Admin', () => {})
//     - visible/hidden per player via the server calling KubeUIActions.setSidebarIconVisible(...),
//       see the "Permission-gated" tab above.
KubeUISidebar.addItem('kubeui_demo:admin', 'minecraft:command_block', 'Admin (server-controlled)', () => {
    KubeUI.alert('Admin', 'Only visible if the server showed it for you.', null)
})
