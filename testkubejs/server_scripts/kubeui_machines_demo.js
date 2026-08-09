// Reference example for the machines & automation system - two
// linked machines (a crusher feeding a smelter) with an upgrade and low-resource/full-queue
// alerts. Each "give" action hands over everything needed to test that one machine immediately -
// the machine item itself, its real input material, and (for the smelter) a lever to power it -
// so there's nothing to go gather first.

// processTicks (the 7th argument) is real game ticks, set directly here in the script - 20 ticks
// per real second at normal speed, so cobblestone -> gravel here takes 100 ticks = 5 real seconds,
// and gravel -> flint takes 140 ticks = 7 seconds. Change either number to retime that one recipe -
// nothing else needs to change.
KubeUIActions.defineMachine('crusher', 'Crusher', 'minecraft:cobblestone', 1, 'minecraft:gravel', 1, 100, 2, 'ignore')
KubeUIActions.defineMachine('smelter', 'Smelter', 'minecraft:gravel', 1, 'minecraft:flint', 1, 140, 2, 'requireSignal')

// A redstone dust in the upgrade slot (the small top-left slot) doubles processing speed and adds
// one extra output item per craft.
KubeUIActions.registerMachineUpgrade('minecraft:redstone', 2.0, 1)

// player.inventory.insertItem(stack, simulate) is the real, already-proven-working KubeJS method
// this codebase's own kubeui_shop_real.js already uses for the same "give a real item" need - not
// player.give(...)/inventory.insert(...), which aren't real methods on this wrapper.
function giveItem(player, itemId, count) {
    let leftover = player.inventory.insertItem(Item.of(itemId, count), false)
    if (leftover.getCount() > 0) {
        player.drop(leftover, false)
    }
}

function giveMachineKit(player, kind, materialItem, materialCount, extraItems) {
    KubeUIActions.giveMachineItem(player, kind)
    giveItem(player, materialItem, materialCount)
    ;(extraItems || []).forEach(entry => giveItem(player, entry.item, entry.count || 1))
}

KubeUIActions.register('kubeui_demo:machines_give_crusher', (player, data) => {
    giveMachineKit(player, 'crusher', 'minecraft:cobblestone', 16, [{ item: 'minecraft:coal', count: 8 }])
    player.tell('§aGot a Crusher + 16 cobblestone + 8 coal.')
    player.tell('§7Place it, right-click to open: put cobblestone in the INPUT slot, gravel comes out in OUTPUT.')
    player.tell('§7It needs energy to run - put coal (or charcoal, a lava bucket, planks, ...) in the FUEL slot,')
    player.tell('§7the same real fuel a furnace burns - watch the orange energy bar fill as it burns.')
})

KubeUIActions.register('kubeui_demo:machines_give_smelter', (player, data) => {
    giveMachineKit(player, 'smelter', 'minecraft:gravel', 16, [{ item: 'minecraft:lever', count: 1 }, { item: 'minecraft:coal', count: 8 }])
    player.tell('§aGot a Smelter + 16 gravel + a lever + 8 coal.')
    player.tell('§7Place it, put gravel in the INPUT slot and coal in the FUEL slot - it only runs while powered,')
    player.tell('§7so place the lever against the machine (or a block next to it) and flip it on.')
})

KubeUIActions.register('kubeui_demo:machines_give_upgrade', (player, data) => {
    giveItem(player, 'minecraft:redstone', 4)
    player.tell('§aGot 4 redstone - drop one in a machine\'s LEFT slot to double its speed and add bonus output.')
})

// Links two placed machines by position (real BlockPos objects straight from the
// click event, held in plain script state - simpler than round-tripping through NBT for a demo).
const linkFrom = {}

// Plain (non-crouching) right-click just opens the menu on its own (see
// KubeUIMachineBlock#useWithoutItem) - real energy now comes from burning fuel in the FUEL slot
// (see KubeUIMachineBlockEntity#tickFuel), the same real vanilla-furnace fuel table
// (level.fuelValues()) a real furnace burns, not a manual top-up. KubeUIActions.chargeMachine(...)
// still exists as the real integration point for a future energy-mod bridge to call from its own
// capability callback - just not wired to a right-click here anymore.
BlockEvents.rightClicked('kubeui:machine', event => {
    if (!event.player.isCrouching()) {
        return
    }
    let key = event.player.uuid

    if (!linkFrom[key]) {
        linkFrom[key] = { level: event.level, pos: event.block.pos }
        event.player.tell('§eLinked from here - shift-right-click another machine to link its output into this one.')
    } else {
        KubeUIActions.linkMachineOutput(event.level, linkFrom[key].pos, event.block.pos)
        KubeUIActions.setMachineNetwork(event.level, linkFrom[key].pos, 'kubeui_demo:line')
        KubeUIActions.setMachineNetwork(event.level, event.block.pos, 'kubeui_demo:line')
        delete linkFrom[key]
        event.player.tell('§aLinked! Both machines joined network "kubeui_demo:line" - check its status from the demo menu.')
    }
})
