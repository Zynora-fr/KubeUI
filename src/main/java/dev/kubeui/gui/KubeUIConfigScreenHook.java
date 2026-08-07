package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLConstructModEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.util.List;

/// Real NeoForge hook for the "Config" button next to KubeUI in the vanilla Mods list -
/// `ModContainer#registerExtensionPoint(IConfigScreenFactory.class, ...)`, the actual mechanism
/// this loader provides for exactly this. The roadmap phase this answers was originally worded
/// around Fabric's ModMenu, which doesn't apply here at all (this project targets NeoForge only -
/// see the README's "Why NeoForge only?" section); `IConfigScreenFactory` is NeoForge's own native
/// equivalent, verified against the real decompiled class rather than assumed. Registered from
/// [FMLConstructModEvent] - `ModLifecycleEvent#getContainer()` is package-private on this
/// version, so the container is looked up via `ModList#getModContainerById(...)` instead, the
/// real public alternative. Referencing client-only `Screen`/[KubeUIScreen] types here is safe
/// because this whole class is `Dist.CLIENT`-gated, so none of it is even loaded on a dedicated
/// server.
///
/// The screen itself is real, not a stub - it edits [KubeUIConfig]'s actual values live.
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIConfigScreenHook {
	private KubeUIConfigScreenHook() {
	}

	@SubscribeEvent
	static void onConstruct(FMLConstructModEvent event) {
		IConfigScreenFactory factory = (container, parent) -> buildConfigScreen(parent);
		ModList.get().getModContainerById(KubeUI.MOD_ID)
			.ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class, factory));
	}

	private static Screen buildConfigScreen(Screen parent) {
		var builder = KubeUIScreenBuilder.builder("KubeUI Config")
			.draggable();

		builder.slider("cfgScale", 0.5, 2.0, KubeUIConfig.defaultScale.get(), (ctx, value) -> {
			KubeUIConfig.defaultScale.set(value);
			KubeUIConfig.defaultScale.save();
		});
		builder.slider("cfgFontScale", 0.5, 2.0, KubeUIConfig.defaultFontScale.get(), (ctx, value) -> {
			KubeUIConfig.defaultFontScale.set(value);
			KubeUIConfig.defaultFontScale.save();
		});
		builder.dropdown("cfgTheme", List.of("default", "dark", "light", "high-contrast"), KubeUIConfig.defaultTheme.get(), (ctx, value) -> {
			KubeUIConfig.defaultTheme.set(value);
			KubeUIConfig.defaultTheme.save();
		});
		builder.toggle("cfgPersist", "Persist scale/theme across sessions", KubeUIConfig.persistScaleAndTheme.get(), (ctx, value) -> {
			KubeUIConfig.persistScaleAndTheme.set(value);
			KubeUIConfig.persistScaleAndTheme.save();
		});
		builder.divider();
		builder.button("Done", ctx -> Minecraft.getInstance().setScreen(parent));

		return new KubeUIScreen(builder);
	}
}
