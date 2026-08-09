// Reference example for the settings hub - three fictional mod sections registered into it
// (one pair deliberately declaring conflicting defaults, to show real conflict detection), search,
// profiles, export/import, and a one-time onboarding screen - built with
// KubeUISettingsHub.register/.matching/.conflicts/.setActiveProfile/.exportProfile.

KubeUISettingsHub.register('bettercombat_demo', 'Combat Tweaks', section => {
    section.label('title', '[Fictional] Better Combat Tweaks')
        .toggle('critHits', 'Enable critical hit indicator', true, (screen, value) => {
            KubeUISettingsHub.setValue(KubeUISettingsHub.activeProfile(), 'global', 'bettercombat.critHits', value)
        })
}, { 'bettercombat.difficulty': 'normal' })

KubeUISettingsHub.register('fancystorage_demo', 'Storage Options', section => {
    section.label('title', '[Fictional] Fancy Storage Options')
        .toggle('autoSort', 'Auto-sort on open', false, (screen, value) => {
            KubeUISettingsHub.setValue(KubeUISettingsHub.activeProfile(), 'global', 'fancystorage.autoSort', value)
        })
    // Deliberately declares the same key as "Combat Tweaks" above, with a different default value -
    // this is exactly what KubeUISettingsHub.conflicts() is meant to catch.
}, { 'bettercombat.difficulty': 'hard' })

KubeUISettingsHub.register('extrabiomes_demo', 'World Generation', section => {
    section.label('title', '[Fictional] Extra Biomes World Gen')
        .label('note', '(purely a display section for this demo - nothing to toggle)')
}, {})

// KubeUISettingsHub.Section is a real Java record - its accessors are the plain component names
// (section.modId()/.name()/.onOpen()), not JavaBean-style getters (no getModId()/.getName()).
function openSettingsSection(section) {
    let sectionBuilder = KubeUI.builder(section.name()).elementSize(260, 20)
    section.onOpen().accept(sectionBuilder)
    sectionBuilder.divider().button('Close', s => s.close()).open()
}

function openSettingsHub(query) {
    query = query || ''

    let builder = KubeUI.builder('KubeUI Settings Hub').elementSize(280, 20)
        .label('title', 'Registered sections:')
        .searchBox('search', (ctx, text) => {
            // `ctx` here is the same real KubeUIContext every other widget callback in this mod
            // calls "screen" - `.close()` is the same real method used everywhere else.
            ctx.close()
            openSettingsHub(text)
        })

    KubeUISettingsHub.matching(query).forEach(section => {
        builder.button(section.modId() + ' - ' + section.name(), s => {
            s.close()
            openSettingsSection(section)
        })
    })

    let conflicts = KubeUISettingsHub.conflicts()
    if (conflicts.length > 0) {
        builder.divider().label('conflictHeader', '§cConflicting settings detected:')
        conflicts.forEach((c, i) => builder.label('conflict' + i, '§7' + c))
    }

    builder.divider()
        .label('profileInfo', 'Active profile: ' + KubeUISettingsHub.activeProfile())
        .button('Switch to "performance"', s => {
            KubeUISettingsHub.setActiveProfile('performance')
            s.close()
            openSettingsHub(query)
        })
        .button('Switch to "quality"', s => {
            KubeUISettingsHub.setActiveProfile('quality')
            s.close()
            openSettingsHub(query)
        })
        .button('Export Active Profile', s => {
            KubeUISettingsHub.exportProfile(KubeUISettingsHub.activeProfile())
            console.log('[kubeui settings hub] exported profile "' + KubeUISettingsHub.activeProfile() + '"')
        })
        .button('Import Active Profile', s => {
            KubeUISettingsHub.importProfile(KubeUISettingsHub.activeProfile())
            s.close()
            openSettingsHub(query)
        })
        .divider()
        .button('Server Dashboard (OP only)', s => {
            s.close()
            s.runServerAction('kubeui_demo:server_dashboard', null)
        })
        .divider()
        .button('Close', s => s.close())
        .open()
}

// Shown once per install - flip the id if you want to see it again during testing. Deferred to
// ClientEvents.loggedIn (the same real "client/world is actually ready" event
// kubeui_test_menu.js's own auto-open already uses), not called at script top level - top-level
// client_scripts code runs during KubeJS's own construction, before Minecraft.getInstance() exists
// yet, and this call needs it (a real, reproduced crash otherwise, not a hypothetical one).
ClientEvents.loggedIn(event => {
    KubeUISettingsHub.showOnboardingOnce('kubeui_demo:onboarding_v1', () => {
        KubeUI.alert(
            'Welcome!',
            'This modpack uses KubeUI\'s settings hub - open it any time from the test menu\nto find every mod\'s config in one place.',
            null
        )
    })
})
