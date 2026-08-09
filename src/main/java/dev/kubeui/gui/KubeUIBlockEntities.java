package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/// Real NeoForge block-entity-type registration for [KubeUIStorageBlockEntity].
public final class KubeUIBlockEntities {
	private KubeUIBlockEntities() {
	}

	private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, KubeUI.MOD_ID);

	/// `BlockEntityType.Builder.of(factory, blocks...).build(null)` - 1.21.1's real construction
	/// path (the same one every vanilla `BlockEntityType` constant uses, decompiled and confirmed) -
	/// `null` for the `DataFixTypes` schema, same as plenty of real vanilla entries with no
	/// datafixer registered for them.
	static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KubeUIStorageBlockEntity>> STORAGE = BLOCK_ENTITIES.register(
		"storage_crate", () -> BlockEntityType.Builder.of(KubeUIStorageBlockEntity::new, KubeUIBlocks.STORAGE.get()).build(null)
	);

	/// [KubeUIMachineBlockEntity].
	static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KubeUIMachineBlockEntity>> MACHINE = BLOCK_ENTITIES.register(
		"machine", () -> BlockEntityType.Builder.of(KubeUIMachineBlockEntity::new, KubeUIBlocks.MACHINE.get()).build(null)
	);

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITIES.register(modEventBus);
	}
}
