// Client-side half of the progression reference example.

function openSkillsDemo() {
    KubeUI.builder('Skills Demo')
        .elementSize(280, 20)
        .label('info', 'Level up (any XP source) for points, or grab a bonus below - then open the tree.')
        .label('info2', 'A permanent XP/level bar is always shown at the bottom of the HUD.')
        .divider()
        .button('Grant 3 Bonus Points', screen => screen.runServerAction('kubeui_demo:skills_bonus_points', null))
        .button('Open Combat Skill Tree', screen => {
            screen.close()
            KubeUI.skillTree('combat')
        })
        .button('Skill Leaderboard', screen => {
            screen.close()
            KubeUI.skillLeaderboard('combat')
        })
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
