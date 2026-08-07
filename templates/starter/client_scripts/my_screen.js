// Starter KubeUI screen - opens on world join. Replace this with your own UI.

function openMyScreen() {
    KubeUI.builder('My Screen')
        .label('status', 'Hello from KubeUI!')
        .button('Say hi to the server', screen => {
            screen.runServerAction('my_addon:say_hi', {})
        })
        .button('Close', screen => screen.close())
        .open()
}

ClientEvents.loggedIn(event => {
    openMyScreen()
})
