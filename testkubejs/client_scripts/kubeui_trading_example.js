// Client half of the trading reference example - see server_scripts/kubeui_trading_example.js for
// where the trade pools are actually defined. There's no "open trade UI" button here on purpose:
// right-clicking a tagged entity opens it automatically (KubeUIVillagerTradeInteraction, real Java
// code, not a script hook) - this screen is just pointers for trying the feature plus a trade
// history check.

function openTradingExampleInfo() {
    KubeUI.builder('Trading Example')
        .label('l1', 'Tag any entity as a trader (run in chat):')
        .label('l2', '/kubeui tag-trader @e[type=minecraft:villager,limit=1,sort=nearest] kubeui_demo:trader')
        .divider()
        .label('l3', 'Then right-click it - the trade screen opens automatically.')
        .label('l4', 'A second pool (kubeui_demo:quest_reward_trader) only unlocks once the')
        .label('l5', 'Quest Board\'s "Gather Wood" quest is completed.')
        .divider()
        .button('Check my trade history', screen => screen.runServerAction('kubeui_demo:trade_history', {}))
        .button('Close', screen => screen.close())
        .open()
}
