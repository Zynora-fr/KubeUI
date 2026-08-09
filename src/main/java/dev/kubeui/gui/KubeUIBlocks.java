package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/// Real NeoForge block registration (`DeferredRegister.createBlocks`, the standard mechanism for a
/// custom block) for [KubeUIStorageBlock] - KubeUI's first real,
/// in-world-placed block. Properties borrow `Blocks.BARREL`'s own (wood-like: axe-harvestable,
/// low blast resistance, wooden sound) rather than inventing new ones, since a storage container
/// behaves like one in every way vanilla cares about.
public final class KubeUIBlocks {
	private KubeUIBlocks() {
	}

	private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(KubeUI.MOD_ID);

	/// `registerBlock(name, factory, propertiesSupplier)`, not the plain `register(name, supplier)`
	/// overload - confirmed the hard way, via a real boot-time crash (`NullPointerException: Block
	/// id not set`, inside `BlockBehaviour.Properties#effectiveDrops`): this version's
	/// `BlockBehaviour.Properties` needs its real registry id set on it *before* the `Block`
	/// constructor runs, which only `registerBlock` actually does (it passes the already-`setId`'d
	/// `Properties` into the factory) - `register` hands the factory a `Properties` with no id at
	/// all, since it never even sees this block's about-to-be-assigned name.
	static final DeferredBlock<KubeUIStorageBlock> STORAGE = BLOCKS.registerBlock("storage_crate", KubeUIStorageBlock::new,
		() -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD)
	);

	/// `kubeui:machine` - properties borrow `Blocks.FURNACE`'s own (stone-like, real
	/// vanilla precedent for a "machine block"), matching every registered kind's shared identity.
	static final DeferredBlock<KubeUIMachineBlock> MACHINE = BLOCKS.registerBlock("machine", KubeUIMachineBlock::new,
		() -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F).sound(SoundType.STONE).requiresCorrectToolForDrops()
	);

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}
}
