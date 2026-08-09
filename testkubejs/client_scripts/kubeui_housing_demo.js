// Client-side half of the housing reference example (see kubeui_housing_demo.js in server_scripts).

function openHousingDemo() {
    KubeUI.builder('Housing Demo')
        .draggable()
        .tab('My Claim', tab => tab
            .elementSize(280, 20)
            .label('info', 'Claims a real 30x30 area, break/place locked to you only.')
            .button('Claim Land Around Me', screen => screen.runServerAction('kubeui_demo:housing_claim', null))
            .button('How to Invite/Rent (see chat)', screen => screen.runServerAction('kubeui_demo:housing_invite_note', null))
        )
        .tab('Homes', tab => tab
            .elementSize(280, 20)
            .button('Set Home "base" Here', screen => screen.runServerAction('kubeui_demo:housing_set_home', null))
            .button('List My Homes', screen => screen.runServerAction('kubeui_demo:housing_list_homes', null))
            .button('Teleport to "base"', screen => screen.runServerAction('kubeui_demo:housing_go_home', null))
        )
        .tab('Decoration Preview', tab => tab
            .elementSize(280, 20)
            .label('previewInfo', 'A real block preview (already-delivered widget) - see it before placing it:')
            .row(row => row
                .blockPreview('minecraft:diamond_block', 1)
                .blockPreview('minecraft:sea_lantern', 1)
                .blockPreview('minecraft:oak_planks', 1)
                .blockPreview('minecraft:cut_copper', 1)
            )
            .button('Close', screen => screen.close())
        )
        .open()
}
