package dev.kubeui.gui;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/// A backpack - a real custom item with its own inventory (see
/// [KubeUIBackpackContainer]), opened via a KubeUI screen rather than vanilla's own item-use screen
/// (there isn't one generic enough to reuse - `.use(...)` opens it directly, no block/placement
/// involved). Reuses real vanilla `ChestMenu` under its own `MenuType` exactly the same way
/// [KubeUIStorageBlockEntity] does, for the same reason (real, already-correct slot/click/quick-
/// move behavior for free).
final class KubeUIBackpackItem extends Item {
	static final int SLOTS = 18;

	KubeUIBackpackItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, InteractionHand hand) {
		var stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel) {
			player.openMenu(new SimpleMenuProvider(
				(containerId, inventory, opener) -> new ChestMenu(KubeUIMenus.BACKPACK.get(), containerId, inventory, new KubeUIBackpackContainer(stack), 2),
				stack.getHoverName()
			));
		}
		return InteractionResultHolder.success(stack);
	}
}
