package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/// Wires each of [KubeUIMenus]'s Recipe Designer `MenuType`s to a [KubeUIRecipeGridScreen] with the
/// matching real vanilla texture, via NeoForge's real `RegisterMenuScreensEvent` (a mod-bus event) -
/// `Dist.CLIENT`-gated like [KubeUIConfigScreenHook], so none of this (or the client-only
/// [KubeUIRecipeGridScreen] it references) is even loaded on a dedicated server.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIRecipeGridScreenHook {
	private KubeUIRecipeGridScreenHook() {
	}

	private static final Identifier CRAFTING_TABLE = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
	private static final Identifier FURNACE = Identifier.withDefaultNamespace("textures/gui/container/furnace.png");
	private static final Identifier BLAST_FURNACE = Identifier.withDefaultNamespace("textures/gui/container/blast_furnace.png");
	private static final Identifier SMOKER = Identifier.withDefaultNamespace("textures/gui/container/smoker.png");
	private static final Identifier STONECUTTER = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
	private static final Identifier SMITHING = Identifier.withDefaultNamespace("textures/gui/container/smithing.png");

	@SubscribeEvent
	static void onRegisterScreens(RegisterMenuScreensEvent event) {
		event.register(KubeUIMenus.CRAFTING.get(), (KubeUIRecipeGridMenu menu, Inventory inv, Component title) -> new KubeUIRecipeGridScreen(menu, inv, title, CRAFTING_TABLE));
		event.register(KubeUIMenus.FURNACE.get(), (KubeUIRecipeGridMenu menu, Inventory inv, Component title) -> new KubeUIRecipeGridScreen(menu, inv, title, FURNACE));
		event.register(KubeUIMenus.BLAST_FURNACE.get(), (KubeUIRecipeGridMenu menu, Inventory inv, Component title) -> new KubeUIRecipeGridScreen(menu, inv, title, BLAST_FURNACE));
		event.register(KubeUIMenus.SMOKER.get(), (KubeUIRecipeGridMenu menu, Inventory inv, Component title) -> new KubeUIRecipeGridScreen(menu, inv, title, SMOKER));
		event.register(KubeUIMenus.STONECUTTER.get(), (KubeUIRecipeGridMenu menu, Inventory inv, Component title) -> new KubeUIRecipeGridScreen(menu, inv, title, STONECUTTER));
		event.register(KubeUIMenus.SMITHING.get(), (KubeUIRecipeGridMenu menu, Inventory inv, Component title) -> new KubeUIRecipeGridScreen(menu, inv, title, SMITHING));
	}
}
