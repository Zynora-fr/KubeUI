package dev.kubeui.gui;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/// `kubeui:machine` - one real block generalizing every script-defined machine kind,
/// same real `BarrelBlock`-mirrored shape as [KubeUIStorageBlock] (`simpleCodec`, `useWithoutItem`
/// opens the block entity's menu, `newBlockEntity`). The one real addition: [#setPlacedBy] copies
/// the placing item's `kind` (from its `DataComponents.CUSTOM_DATA`, the same component
/// [KubeUITraderEggItem] already writes) onto the freshly-placed [KubeUIMachineBlockEntity] - the
/// one moment a placed block actually learns which registered kind it is.
public final class KubeUIMachineBlock extends BaseEntityBlock {
	public static final MapCodec<KubeUIMachineBlock> CODEC = simpleCodec(KubeUIMachineBlock::new);

	public KubeUIMachineBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<KubeUIMachineBlock> codec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KubeUIMachineBlockEntity(pos, state);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && level.getBlockEntity(pos) instanceof KubeUIMachineBlockEntity machine) {
			var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
			machine.setKind(KubeUINbtCompat.getStringOr(tag, "kind", ""));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof KubeUIMachineBlockEntity machine) {
			player.openMenu(machine);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return KubeUIBlockRenderSupport.useModelRenderShape() ? RenderShape.MODEL : RenderShape.INVISIBLE;
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, KubeUIBlockEntities.MACHINE.get(), KubeUIMachineBlockEntity::serverTick);
	}
}
