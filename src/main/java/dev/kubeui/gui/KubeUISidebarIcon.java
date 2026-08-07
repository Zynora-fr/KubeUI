package dev.kubeui.gui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/// One icon registered via [KubeUISidebar]. Exactly one of `itemIcon`/`textureIcon` is set -
/// which one determines how [KubeUISidebarWidget] renders it.
///
/// Stores the `Item`, not a built `ItemStack` - scripts register icons once at script load
/// (mod-construction time), before item registries have their components bound yet, so building
/// the `ItemStack` has to wait until a screen actually needs it (see [KubeUISidebarWidget]).
record KubeUISidebarIcon(String id, Item itemIcon, ResourceLocation textureIcon, String tooltip, Runnable onClick) {
}
