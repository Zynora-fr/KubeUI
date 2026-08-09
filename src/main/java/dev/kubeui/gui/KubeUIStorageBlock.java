package dev.kubeui.gui;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

/// KubeUI's first real, in-world-placed block - a storage container
/// backed by a genuine [KubeUIStorageBlockEntity]/[net.minecraft.world.Container], not a client-side
/// simulation. Modeled directly on real vanilla `BarrelBlock` (decompiled and mirrored, not
/// guessed): a plain static `MapCodec` via `simpleCodec(...)`, `useWithoutItem` opens the block
/// entity's menu (it implements `MenuProvider`, checked via `BaseEntityBlock#getMenuProvider`).
/// Unlike `BarrelBlock`, no `FACING`/`OPEN` blockstate properties - a fixed orientation keeps the
/// blockstate/model resources to the simplest possible real form (one variant, no rotation logic to
/// get wrong without being able to visually verify it in this environment).
public final class KubeUIStorageBlock extends BaseEntityBlock {
	public static final MapCodec<KubeUIStorageBlock> CODEC = simpleCodec(KubeUIStorageBlock::new);

	public KubeUIStorageBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<KubeUIStorageBlock> codec() {
		return CODEC;
	}

	@Override
	public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new KubeUIStorageBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof KubeUIStorageBlockEntity storage && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			storage.setOwnerIfAbsent(serverPlayer);
			if (!storage.canOpen(serverPlayer)) {
				player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("kubeui.storage.locked"));
				return InteractionResult.CONSUME;
			}
			KubeUIStorageSessions.set(serverPlayer, pos);
			player.openMenu(storage);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock())) {
			level.updateNeighbourForOutputSignal(pos, this);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
