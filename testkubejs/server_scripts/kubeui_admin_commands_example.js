// Example: a fully custom admin command, built entirely in a script, with no Java changes needed.
// KubeUI itself already ships a real `/money deposit`/`/money withdraw` (OP-only) command - this
// file shows the actual mechanism anyone can use to add their *own* admin commands on top of
// KubeUIActions, using KubeJS's own real `ServerEvents.commandRegistry` event (not a KubeUI
// invention - the same event any KubeJS command-adding pack uses). Adapt the two subcommands
// below for whatever an admin needs: grant currency, reset a shop's stock, wipe a quest, etc.
//
// `event.commands`/`event.arguments` are KubeJS's real wrappers around vanilla `Commands`/its
// argument-type helpers - `Arguments.PLAYER.create(event)` builds the real `EntityArgument.player()`
// type, and `Arguments.PLAYER.getResult(ctx, name)` resolves it back to a real ServerPlayer once
// the command runs, the same objects `KubeUIActions.pay`/`.charge` already expect.
//
// Permission gating: `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` is the real, current
// vanilla permission check (this MC version replaced the old numeric op-level 2 with a proper
// Permission/PermissionCheck system) - the same one KubeUI's own `/money deposit`/`/money
// withdraw` use in Java, so this command needs the same permission a real admin command should.
ServerEvents.commandRegistry(event => {
    // Explicit getCommands()/getArguments() rather than the .commands/.arguments bean-property
    // shorthand - this codebase already found one real case (ItemStack#getCount(), see the
    // comment in kubeui_shop_real.js's git history) where Rhino's bean-property mapping silently
    // returned undefined instead of calling through, so explicit getters are the safer default
    // for a wrapper type not already proven to work as a bare property elsewhere in this project.
    const Commands = event.getCommands()
    const Arguments = event.getArguments()

    event.register(
        Commands.literal('kubeuiadmin')
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal('addmoney')
                .then(Commands.argument('target', Arguments.PLAYER.create(event))
                    .then(Commands.argument('amount', Arguments.LONG.create(event))
                        .then(Commands.argument('currency', Arguments.WORD.create(event))
                            .executes(ctx => {
                                let target = Arguments.PLAYER.getResult(ctx, 'target')
                                let amount = Arguments.LONG.getResult(ctx, 'amount')
                                let currency = Arguments.WORD.getResult(ctx, 'currency')

                                KubeUIActions.pay(target, currency, amount)
                                let newBalance = KubeUIActions.balance(target, currency)
                                ctx.getSource().sendSystemMessage(Text.of('Added ' + amount + ' ' + currency + ' to ' + target.username + ' (new balance: ' + newBalance + ').').green())
                                target.tell('§aAn admin added ' + amount + ' ' + currency + ' to your balance.')
                                return 1
                            })))))
            .then(Commands.literal('removemoney')
                .then(Commands.argument('target', Arguments.PLAYER.create(event))
                    .then(Commands.argument('amount', Arguments.LONG.create(event))
                        .then(Commands.argument('currency', Arguments.WORD.create(event))
                            .executes(ctx => {
                                let target = Arguments.PLAYER.getResult(ctx, 'target')
                                let amount = Arguments.LONG.getResult(ctx, 'amount')
                                let currency = Arguments.WORD.getResult(ctx, 'currency')

                                if (!KubeUIActions.charge(target, currency, amount)) {
                                    ctx.getSource().sendSystemMessage(Text.of(target.username + ' only has ' + KubeUIActions.balance(target, currency) + ' ' + currency + ' - can\'t remove ' + amount + '.').red())
                                    return 0
                                }
                                let newBalance = KubeUIActions.balance(target, currency)
                                ctx.getSource().sendSystemMessage(Text.of('Removed ' + amount + ' ' + currency + ' from ' + target.username + ' (new balance: ' + newBalance + ').').green())
                                target.tell('§cAn admin removed ' + amount + ' ' + currency + ' from your balance.')
                                return 1
                            })))))
    )
})
