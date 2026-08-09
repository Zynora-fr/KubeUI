package dev.kubeui.plugin;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIActions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/// Real server-side commands (unlike [KubeUIDebugCommands], which is client-only) - registered via
/// [RegisterCommandsEvent], the vanilla/NeoForge mechanism for a command that needs actual server
/// data (here, an entity's trade-pool/quest-giver state, or a player's currency balance, only ever
/// stored server-side). `/money` is the real top-level command layered on
/// `KubeUIActions.pay`/`.charge`/`.transferCurrency`: `balance`/`pay` are self-service (any
/// player), `deposit`/`withdraw` require `Commands.LEVEL_GAMEMASTERS` (the same real permission
/// check `/gamemode` etc. use) since they mint/destroy currency out of thin air rather than moving
/// it between two real players. Permission gating on this version is a plain numeric op-level
/// check (`source.hasPermission(Commands.LEVEL_GAMEMASTERS)`) - no `Commands.hasPermission(int)`
/// static helper exists here yet (a later Minecraft version adds one).
@EventBusSubscriber(modid = KubeUI.MOD_ID)
final class KubeUIServerCommands {
	private KubeUIServerCommands() {
	}

	@SubscribeEvent
	static void register(RegisterCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("kubeui")
				.then(Commands.literal("villager-trades")
					.then(Commands.argument("target", EntityArgument.entity())
						.executes(KubeUIServerCommands::villagerTrades)))
				.then(Commands.literal("tag-trader")
					.then(Commands.argument("target", EntityArgument.entity())
						.then(Commands.argument("poolId", StringArgumentType.string())
							.executes(KubeUIServerCommands::tagTrader))))
				.then(Commands.literal("tag-quest-giver")
					.then(Commands.argument("target", EntityArgument.entity())
						.then(Commands.argument("questIds", StringArgumentType.greedyString())
							.executes(KubeUIServerCommands::tagQuestGiver))))
				.then(Commands.literal("tag-dialogue-npc")
					.then(Commands.argument("target", EntityArgument.entity())
						.then(Commands.argument("dialogueId", StringArgumentType.string())
							.executes(KubeUIServerCommands::tagDialogueNpc))))
		);

		event.getDispatcher().register(
			Commands.literal("money")
				.executes(KubeUIServerCommands::moneyUsage)
				.then(Commands.literal("balance")
					.executes(KubeUIServerCommands::moneyBalanceAll)
					.then(Commands.argument("currency", StringArgumentType.word())
						.executes(ctx -> moneyBalance(ctx, StringArgumentType.getString(ctx, "currency")))))
				.then(Commands.literal("pay")
					.then(Commands.argument("target", EntityArgument.player())
						.then(Commands.argument("amount", LongArgumentType.longArg(1))
							.then(Commands.argument("currency", StringArgumentType.word())
								.executes(KubeUIServerCommands::moneyPay)))))
				.then(Commands.literal("deposit")
					.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("target", EntityArgument.player())
						.then(Commands.argument("amount", LongArgumentType.longArg(1))
							.then(Commands.argument("currency", StringArgumentType.word())
								.executes(KubeUIServerCommands::moneyDeposit)))))
				.then(Commands.literal("withdraw")
					.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
					.then(Commands.argument("target", EntityArgument.player())
						.then(Commands.argument("amount", LongArgumentType.longArg(1))
							.then(Commands.argument("currency", StringArgumentType.word())
								.executes(KubeUIServerCommands::moneyWithdraw)))))
		);
	}

	private static int villagerTrades(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var target = EntityArgument.getEntity(ctx, "target");
		var source = ctx.getSource();
		if (!(source.getEntity() instanceof ServerPlayer viewer)) {
			source.sendSystemMessage(Component.literal("This command needs a player as the command source (trades can be gated per player)."));
			return 0;
		}

		source.sendSystemMessage(Component.literal(KubeUIActions.describeTrades(target, viewer)));
		return 1;
	}

	/// Tags any existing entity as a trader without needing a script to spawn/look one up itself -
	/// real vanilla entity-selector syntax (`@e[type=...]`, `@n`, ...) already does the "find an
	/// entity" part, so this doesn't need its own lookup mechanism.
	private static int tagTrader(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var target = EntityArgument.getEntity(ctx, "target");
		String poolId = StringArgumentType.getString(ctx, "poolId");
		KubeUIActions.tagTradePool(target, poolId);
		ctx.getSource().sendSystemMessage(Component.literal(target.getName().getString() + " is now trading pool '" + poolId + "' - right-click it to see."));
		return 1;
	}

	/// Same "tag any existing entity, real vanilla entity-selector syntax does the lookup" shape as
	/// [#tagTrader] - `questIds` is comma-separated (Brigadier has no built-in "list of strings"
	/// argument type, and a greedy string is the simplest real one that does exist) since a single
	/// entity offering several quests at once is the expected common case, not an edge case.
	private static int tagQuestGiver(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var target = EntityArgument.getEntity(ctx, "target");
		String raw = StringArgumentType.getString(ctx, "questIds");
		var questIds = java.util.Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
		KubeUIActions.tagQuestGiver(target, questIds);
		ctx.getSource().sendSystemMessage(Component.literal(
			target.getName().getString() + " now offers " + questIds.size() + " quest(s) - right-click it to see."
		));
		return 1;
	}

	/// Same "tag any existing entity" shape as [#tagTrader]/[#tagQuestGiver] - the concrete
	/// "click an NPC" trigger for `KubeUIActions.defineDialogue(...)`.
	private static int tagDialogueNpc(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var target = EntityArgument.getEntity(ctx, "target");
		String dialogueId = StringArgumentType.getString(ctx, "dialogueId");
		KubeUIActions.tagDialogueNpc(target, dialogueId);
		ctx.getSource().sendSystemMessage(Component.literal(
			target.getName().getString() + " now starts dialogue '" + dialogueId + "' - right-click it to see."
		));
		return 1;
	}

	private static int moneyUsage(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
		ctx.getSource().sendSystemMessage(Component.literal(
			"/money balance [currency] - /money pay <player> <amount> <currency> - "
				+ "/money deposit <player> <amount> <currency> - /money withdraw <player> <amount> <currency> (deposit/withdraw need OP)"
		));
		return 1;
	}

	private static int moneyBalanceAll(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var source = ctx.getSource();
		var player = source.getPlayerOrException();
		var currencies = KubeUIActions.knownCurrencies();

		if (currencies.isEmpty()) {
			source.sendSystemMessage(Component.literal("No currencies have been registered yet - specify one with /money balance <currency>."));
			return 0;
		}

		var sorted = new java.util.ArrayList<>(currencies);
		sorted.sort(String::compareTo);
		var summary = new StringBuilder(player.getName().getString() + "'s balances:");
		for (var currency : sorted) {
			summary.append("\n  ").append(currency).append(": ").append(KubeUIActions.balance(player, currency));
		}
		source.sendSystemMessage(Component.literal(summary.toString()));
		return 1;
	}

	private static int moneyBalance(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String currency) throws CommandSyntaxException {
		var source = ctx.getSource();
		var player = source.getPlayerOrException();
		source.sendSystemMessage(Component.literal(player.getName().getString() + "'s " + currency + " balance: " + KubeUIActions.balance(player, currency)));
		return 1;
	}

	private static int moneyPay(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var source = ctx.getSource();
		var from = source.getPlayerOrException();
		var to = EntityArgument.getPlayer(ctx, "target");
		long amount = LongArgumentType.getLong(ctx, "amount");
		String currency = StringArgumentType.getString(ctx, "currency");

		if (from.getUUID().equals(to.getUUID())) {
			source.sendSystemMessage(Component.literal("You can't pay yourself."));
			return 0;
		}
		if (!KubeUIActions.transferCurrency(from, to, currency, amount)) {
			source.sendSystemMessage(Component.literal("Payment failed - not enough " + currency + " (have " + KubeUIActions.balance(from, currency) + ")."));
			return 0;
		}
		source.sendSystemMessage(Component.literal("Paid " + amount + " " + currency + " to " + to.getName().getString() + "."));
		return 1;
	}

	private static int moneyDeposit(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var target = EntityArgument.getPlayer(ctx, "target");
		long amount = LongArgumentType.getLong(ctx, "amount");
		String currency = StringArgumentType.getString(ctx, "currency");

		KubeUIActions.pay(target, currency, amount);
		ctx.getSource().sendSystemMessage(Component.literal("Deposited " + amount + " " + currency + " into " + target.getName().getString() + "'s account (new balance: " + KubeUIActions.balance(target, currency) + ")."));
		return 1;
	}

	private static int moneyWithdraw(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		var target = EntityArgument.getPlayer(ctx, "target");
		long amount = LongArgumentType.getLong(ctx, "amount");
		String currency = StringArgumentType.getString(ctx, "currency");

		if (!KubeUIActions.charge(target, currency, amount)) {
			ctx.getSource().sendSystemMessage(Component.literal(target.getName().getString() + " only has " + KubeUIActions.balance(target, currency) + " " + currency + " - can't withdraw " + amount + "."));
			return 0;
		}
		ctx.getSource().sendSystemMessage(Component.literal("Withdrew " + amount + " " + currency + " from " + target.getName().getString() + "'s account (new balance: " + KubeUIActions.balance(target, currency) + ")."));
		return 1;
	}
}
