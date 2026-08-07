package dev.kubeui.gui;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.ArrayList;

/// The item half of the custom trader system (`/kubeui trader-designer`) - a real, giftable,
/// storable item (not a tag applied to an already-existing entity) that carries its own baked-in
/// trade list plus AI/movement flags in its `CustomData` component, and spawns a real
/// [net.minecraft.world.entity.npc.Villager] with exactly that configuration when used on a block -
/// same real positioning logic vanilla's own `SpawnEggItem#useOn` uses (spawn on top of a solid
/// block, at the clicked position for a passable one).
///
/// Spawning goes through the genuinely real [EntityType#spawn] (not manual `new Villager(...)` +
/// `addFreshEntity`), so it gets the same finalization vanilla spawn eggs do. The trades themselves
/// don't reuse vanilla's `VillagerTrades`/`MerchantOffer` system - see [KubeUIVillagerTrades]'s
/// class doc for why - they're baked directly onto the spawned entity's own persistent data via
/// [KubeUIVillagerTrades#tagInlineTrades], not a named pool (unlike
/// `KubeUIActions.registerTradePool`/`.tagTradePool`'s script-defined traders) - a real entity's
/// persistent data survives a server restart, while a named pool registered only at spawn time
/// with no script re-registering it on every boot would not.
final class KubeUITraderEggItem extends Item {
	KubeUITraderEggItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		var level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}

		var stack = context.getItemInHand();
		var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		var tradesTag = tag.getListOrEmpty("trades");
		if (tradesTag.isEmpty()) {
			player.sendSystemMessage(Component.literal("This trader egg has no trades baked in - it can't be used."));
			return InteractionResult.FAIL;
		}

		var pos = context.getClickedPos();
		var clickedFace = context.getClickedFace();
		var blockState = level.getBlockState(pos);
		var spawnPos = blockState.getCollisionShape(level, pos).isEmpty() ? pos : pos.relative(clickedFace);

		var villager = EntityType.VILLAGER.spawn(
			serverLevel, stack, player, spawnPos, EntitySpawnReason.SPAWN_ITEM_USE, true, clickedFace == net.minecraft.core.Direction.UP
		);
		if (villager == null) {
			return InteractionResult.FAIL;
		}

		var trades = new ArrayList<KubeUITradeDef>();
		for (var entry : tradesTag) {
			if (entry instanceof net.minecraft.nbt.CompoundTag tradeTag) {
				trades.add(KubeUIVillagerTrades.tradeFromTag(tradeTag));
			}
		}

		KubeUIVillagerTrades.tagInlineTrades(villager, trades);

		boolean hasAI = tag.getBooleanOr("hasAI", true);
		boolean canMove = tag.getBooleanOr("canMove", true);
		villager.setNoAi(!hasAI);
		if (!canMove) {
			var speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.setBaseValue(0.0);
			}
		}

		stack.consume(1, player);
		return InteractionResult.SUCCESS;
	}
}
