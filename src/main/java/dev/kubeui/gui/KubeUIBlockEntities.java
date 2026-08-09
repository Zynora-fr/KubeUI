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

	/// `BlockEntityType`'s public constructor (real, confirmed by an actual `javac` error message
	/// after a first guess based on a different, less-patched classpath jar was wrong - no `Builder`
	/// nested class exists in this version at all) takes the supplier plus a `Block...` varargs of
	/// every block this type is valid for.
	static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KubeUIStorageBlockEntity>> STORAGE = BLOCK_ENTITIES.register(
		"storage_crate", () -> new BlockEntityType<KubeUIStorageBlockEntity>(KubeUIStorageBlockEntity::new, KubeUIBlocks.STORAGE.get())
	);

	/// [KubeUIMachineBlockEntity].
	static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KubeUIMachineBlockEntity>> MACHINE = BLOCK_ENTITIES.register(
		"machine", () -> new BlockEntityType<KubeUIMachineBlockEntity>(KubeUIMachineBlockEntity::new, KubeUIBlocks.MACHINE.get())
	);

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITIES.register(modEventBus);
	}
}
