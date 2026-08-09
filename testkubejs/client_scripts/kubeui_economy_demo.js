// Client-side half of the economy reference example (see kubeui_economy_demo.js in
// server_scripts) - every button here is just runServerAction/KubeUI.* round trips, the client
// never computes or trusts a balance itself.

function openEconomyDemo() {
    KubeUI.builder('Economy Demo')
        .elementSize(280, 20)
        .label('info', 'A scriptable "gold" currency, independent of any physical item.')
        .label('cmdInfo', 'Also usable from chat: /money balance gold, /money pay <player> <amount> gold,')
        .label('cmdInfo2', 'and /money deposit|withdraw <player> <amount> gold (needs OP).')
        .divider()
        .button('Earn 20 gold', screen => screen.runServerAction('kubeui_demo:economy_earn', null))
        .button('Spend 15 gold', screen => screen.runServerAction('kubeui_demo:economy_spend', null))
        .divider()
        .label('exchangeInfo', 'Item <-> currency exchange:')
        .button('Sell 8 rotten flesh for 3 gold', screen => screen.runServerAction('kubeui_demo:economy_sell_junk', null))
        .button('Buy 8 rotten flesh for 3 gold', screen => screen.runServerAction('kubeui_demo:economy_buy_junk', null))
        .divider()
        .button('Transaction History', screen => {
            screen.close()
            KubeUI.currencyHistory('gold')
        })
        .button('Leaderboard', screen => {
            screen.close()
            KubeUI.leaderboard('gold')
        })
        .button('Open Shop (buy/sell, fluctuating prices)', screen => {
            screen.close()
            KubeUI.shop('kubeui_demo:general_store')
        })
        .divider()
        .button('Close', screen => screen.close())
        .open()
}
