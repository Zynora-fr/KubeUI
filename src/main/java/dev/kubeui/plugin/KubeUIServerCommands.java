package dev.kubeui.plugin;

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
/// data (here, an entity's trade-pool/quest-giver state, only ever stored server-side).
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
}
