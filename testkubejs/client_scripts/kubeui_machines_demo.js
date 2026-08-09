// Client-side half of the machines reference example.

function openMachinesDemo() {
    KubeUI.builder('Machines Demo')
        .elementSize(300, 20)
        .label('step1', '1. Get a kit below, place the machine, right-click to open it.')
        .label('step2', '2. Slots: LEFT = upgrade, MIDDLE = input (feed it here), RIGHT = output.')
        .label('step3', '3. The Smelter only runs while powered - flip the lever it comes with.')
        .label('step4', '4. Shift-right-click the Crusher then the Smelter to link + join the demo network.')
        .divider()
        .button('Get Crusher Kit (crusher + 16 cobblestone)', screen => screen.runServerAction('kubeui_demo:machines_give_crusher', null))
        .button('Get Smelter Kit (smelter + 16 gravel + lever)', screen => screen.runServerAction('kubeui_demo:machines_give_smelter', null))
        .button('Get Upgrade (4 redstone)', screen => screen.runServerAction('kubeui_demo:machines_give_upgrade', null))
        .divider()
        .button('View Network Status (progress, energy, crafted chart)', screen => {
            screen.close()
            KubeUI.machineNetworkStatus('kubeui_demo:line')
        })
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
