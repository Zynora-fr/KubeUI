// Client-side half of the storage reference example.

function openStorageDemo() {
    KubeUI.builder('Storage Demo')
        .elementSize(300, 20)
        .label('info', 'Real placed blocks: /give @s kubeui:storage_crate, /give @s kubeui:backpack.')
        .label('info2', 'Right-click a placed crate for sort/search/settings (filter, lock, authorize, link to a network).')
        .divider()
        .textField('networkId', '', 'e.g. guild_storage', (screen, value) => {})
        .button('View Network Contents', screen => {
            let id = screen.getTextFieldValue('networkId')
            if (id) {
                screen.close()
                KubeUI.storageNetworkView(id)
            }
        })
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
