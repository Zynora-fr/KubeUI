// Real, live demo of KubeUIHud - a fully custom HUD bar built entirely from JS (no Java involved)
// tracking the local player's own health, positioned like a boss bar. Proves "on peut créer nos
// propres GUI et UI en JS" for continuously-rendered HUD overlays, not just modal screens opened
// with KubeUI.builder(...).open() - every *other* HUD element in this mod (the real boss bar,
// combat log, waypoint tracker, ...) is still hardcoded Java with zero script control over its
// look; KubeUIHud is the general escape hatch for that. Positioned lower than the real boss bar
// (y: 40, not 4) purely so the two demos don't visually overlap if both happen to be active.
//
// KubeUIHud.setBar/.setLabel take a plain options object - every field has a sensible default, so
// a script only needs to pass what it actually wants to change. Call .setBar(sameId, ...) again
// any time the value should update (every tick here, since health changes constantly) - it's a
// real replace, not additive. .removeBar(id)/.removeLabel(id)/.clear() take it back off screen.
ClientEvents.tick(event => {
    let player = event.player
    KubeUIHud.setBar('kubeui_demo:health_bar', {
        anchor: 'topCenter', x: 0, y: 40,
        width: 180, height: 8,
        value: player.health, max: player.maxHealth,
        barColor: 0xFF30C030, bgColor: 0xFF202020, borderColor: 0xFF000000,
        label: 'Health (custom HUD, 100% JS)', labelColor: 0xFFFFFFFF
    })
})
