package dev.kubeui.gui;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;

/// Real, persisted inventory for [KubeUIBackpackItem] - backed by the real vanilla
/// `DataComponents.CONTAINER`/`ItemContainerContents` component (the same one a shulker box's own
/// contents use), not a custom NBT tag. Correct persistence across death/pickup falls out of that
/// for free: the component travels with whatever `ItemStack` the backpack item *is*, wherever that
/// stack goes (dropped, picked back up, moved between inventories) - nothing here needs its own
/// save/load hook.
final class KubeUIBackpackContainer extends SimpleContainer {
	private final ItemStack backpackStack;

	KubeUIBackpackContainer(ItemStack backpackStack) {
		super(KubeUIBackpackItem.SLOTS);
		this.backpackStack = backpackStack;

		var items = NonNullList.withSize(KubeUIBackpackItem.SLOTS, ItemStack.EMPTY);
		backpackStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		for (int i = 0; i < items.size(); i++) {
			setItem(i, items.get(i));
		}
	}

	@Override
	public void setChanged() {
		super.setChanged();
		var items = new ArrayList<ItemStack>(getContainerSize());
		for (int i = 0; i < getContainerSize(); i++) {
			items.add(getItem(i));
		}
		backpackStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}
}
