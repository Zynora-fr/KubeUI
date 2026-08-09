// Reference example for the combat/status HUD systems - a scriptable boss fight with a custom
// boss bar (color/text phases), a custom "Enflammé de rage" status ticking its own real cost, a
// skill cooldown, a toggleable combat log, and a post-fight recap - built with
// KubeUIActions.showBossBar/.defineStatus/.startCooldown/combatHistory and KubeUICombatEvents.

KubeUIActions.registerCurrency('gold')
KubeUIActions.defineStatus('enflamme', 'Enflammé de rage', 'minecraft:blaze_powder')

const BOSS_BAR_ID = 'kubeui_demo:boss'
const BOSS_MAX_HEALTH = 40
const bossHealth = {} // player uuid -> current boss health, while a fight is active

function bossPhases() {
    return [
        { threshold: 1.0, color: 0xFF33CC55, text: 'Calm' },
        { threshold: 0.5, color: 0xFFE0A030, text: 'Enraged' },
        { threshold: 0.2, color: 0xFFCC3333, text: 'Final Stand' },
    ]
}

KubeUIActions.register('kubeui_demo:combat_start_boss', (player, data) => {
    bossHealth[player.uuid] = BOSS_MAX_HEALTH
    KubeUIActions.showBossBar([player], BOSS_BAR_ID, 'Ragefang the Cursed', BOSS_MAX_HEALTH, BOSS_MAX_HEALTH, bossPhases())
    player.tell('§cRagefang the Cursed appears! Click "Attack" to fight - watch the bar change')
    player.tell('§ccolor/text as it crosses the Enraged (50%) and Final Stand (20%) thresholds.')
})

KubeUIActions.register('kubeui_demo:combat_attack_boss', (player, data) => {
    if (!(player.uuid in bossHealth)) {
        player.tell('§cNo active fight - start one first.')
        return
    }
    let health = Math.max(0, bossHealth[player.uuid] - (4 + Math.floor(Math.random() * 5)))
    bossHealth[player.uuid] = health

    if (health <= 0) {
        delete bossHealth[player.uuid]
        KubeUIActions.hideBossBar([player], BOSS_BAR_ID)
        player.tell('§aRagefang the Cursed is defeated!')
        return
    }

    KubeUIActions.updateBossBarHealth([player], BOSS_BAR_ID, 'Ragefang the Cursed', health, BOSS_MAX_HEALTH)
    // The boss claws back with a real, ticking status - see the statusTick handler below for its
    // actual "damage over time" cost (spent gold, not raw HP - see that handler's own comment).
    KubeUIActions.applyStatus(player, 'enflamme', 60)
})

// A status here has no built-in effect of its own (see KubeUIStatusEffects's own doc) - this is
// its *entire* "Enflammé de rage" behavior: once per real second, while it's active, it costs the
// player 1 gold, a real, already-proven-safe way to demonstrate "the status does something" without
// guessing at a raw player-damage API this session has no way to verify against a live game.
KubeUICombatEvents.statusTick(event => {
    if (event.statusId !== 'enflamme' || event.remainingTicks % 20 !== 0) {
        return
    }
    if (KubeUIActions.charge(event.player, 'gold', 1)) {
        event.player.tell('§6🔥 Enflammé de rage burns for 1 gold.')
    }
})

// A real post-combat recap, built the moment KubeUICombatLog itself decides the fight's over (no
// hit either way for a few seconds) - not just available on request via combatHistory().
KubeUICombatEvents.combatEnded(event => {
    let player = event.player
    player.tell('§b--- Combat Summary ---')
    player.tell('§7Damage dealt: §f' + Math.round(event.damageDealt) + '§7  Damage taken: §f' + Math.round(event.damageTaken))
    player.tell('§7Duration: §f' + Math.round(event.durationTicks / 20) + 's  §7Result: ' + (event.victory ? '§aVictory' : '§cDefeat'))
})

KubeUIActions.register('kubeui_demo:combat_toggle_log', (player, data) => {
    let enabled = data.getBooleanOr('enabled', true)
    KubeUIActions.setCombatLogEnabled(player, enabled)
    player.tell(enabled ? '§aCombat log HUD enabled.' : '§7Combat log HUD disabled.')
})

KubeUIActions.register('kubeui_demo:combat_use_skill', (player, data) => {
    if (KubeUIActions.isCooldownActive(player, 'power_strike')) {
        player.tell('§cPower Strike is on cooldown (' + Math.ceil(KubeUIActions.cooldownRemaining(player, 'power_strike') / 20) + 's left).')
        return
    }
    KubeUIActions.startCooldown(player, 'power_strike', 100) // 5 real seconds
    player.tell('§dPower Strike used! (5s cooldown - watch the bar above the hotbar)')
})

KubeUIActions.register('kubeui_demo:combat_show_aoe', (player, data) => {
    KubeUIActions.showAoeIndicator(player, 5, 0xFFE04040)
    player.tell('§7Showing a 5-block attack range indicator - "Hide AOE" to clear it.')
})

KubeUIActions.register('kubeui_demo:combat_hide_aoe', (player, data) => {
    KubeUIActions.hideAoeIndicator(player)
})

// A confirmation before a genuinely risky action (KubeUI.confirm() itself already existed - this
// is the concrete case it asked for) - spending a rare item the player can't get back.
KubeUIActions.register('kubeui_demo:combat_risky_action', (player, data) => {
    player.tell('§7(Client asked to confirm - see the dialog.)')
})

KubeUIActions.register('kubeui_demo:combat_history_request', (player, data) => {
    let history = KubeUIActions.combatHistory(player)
    if (history.length === 0) {
        player.tell('§7No tracked fights yet - start a boss fight and let it run its course.')
        return
    }
    player.tell('§b--- Combat History (most recent first) ---')
    history.forEach(entry => {
        let result = entry.getBooleanOr('victory', false) ? '§aVictory' : '§cDefeat'
        let dealt = Math.round(entry.getFloatOr('damageDealt', 0))
        let taken = Math.round(entry.getFloatOr('damageTaken', 0))
        let seconds = Math.round(entry.getLongOr('durationTicks', 0) / 20)
        player.tell(result + '§7 - dealt ' + dealt + ', took ' + taken + ', ' + seconds + 's')
    })
})
