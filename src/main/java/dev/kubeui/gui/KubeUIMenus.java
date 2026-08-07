package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/// Real NeoForge menu-type registration (`DeferredRegister<MenuType<?>>` against `Registries.MENU`,
/// the standard mechanism for a custom container menu) for [KubeUIRecipeGridMenu] - one `MenuType`
/// per real vanilla screen family the Recipe Designer's grid can look like (crafting table, furnace,
/// blast furnace, smoker, stonecutter, smithing table), since vanilla's own shared `MenuType`s are
/// each hardcoded to one specific screen in `MenuScreens`'s static initializer with no way to re-skin
/// them per use, and each kind now gets the real matching vanilla look instead of a generic chest.
///
/// Each `MenuType`'s bare 2-arg constructor (used only for the client's own local instance,
/// reconstructed from `ClientboundOpenScreenPacket` - see [KubeUIRecipeGridMenu]'s class doc)
/// hardcodes the one kind that type is for, so client and server always agree on layout/slot count
/// without needing to send extra data.
public final class KubeUIMenus {
	private KubeUIMenus() {
	}

	private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, KubeUI.MOD_ID);

	static final Supplier<MenuType<KubeUIRecipeGridMenu>> CRAFTING = MENUS.register(
		"recipe_grid_crafting", () -> new MenuType<>((id, inv) -> new KubeUIRecipeGridMenu(id, inv, "shapeless"), FeatureFlags.VANILLA_SET)
	);
	static final Supplier<MenuType<KubeUIRecipeGridMenu>> FURNACE = MENUS.register(
		"recipe_grid_furnace", () -> new MenuType<>((id, inv) -> new KubeUIRecipeGridMenu(id, inv, "smelting"), FeatureFlags.VANILLA_SET)
	);
	static final Supplier<MenuType<KubeUIRecipeGridMenu>> BLAST_FURNACE = MENUS.register(
		"recipe_grid_blast_furnace", () -> new MenuType<>((id, inv) -> new KubeUIRecipeGridMenu(id, inv, "blasting"), FeatureFlags.VANILLA_SET)
	);
	static final Supplier<MenuType<KubeUIRecipeGridMenu>> SMOKER = MENUS.register(
		"recipe_grid_smoker", () -> new MenuType<>((id, inv) -> new KubeUIRecipeGridMenu(id, inv, "smoking"), FeatureFlags.VANILLA_SET)
	);
	static final Supplier<MenuType<KubeUIRecipeGridMenu>> STONECUTTER = MENUS.register(
		"recipe_grid_stonecutter", () -> new MenuType<>((id, inv) -> new KubeUIRecipeGridMenu(id, inv, "stonecutting"), FeatureFlags.VANILLA_SET)
	);
	static final Supplier<MenuType<KubeUIRecipeGridMenu>> SMITHING = MENUS.register(
		"recipe_grid_smithing", () -> new MenuType<>((id, inv) -> new KubeUIRecipeGridMenu(id, inv, "smithing"), FeatureFlags.VANILLA_SET)
	);

	/// Which registered `MenuType` a given recipe `kind` opens with - called lazily at menu
	/// construction time (never during static init), so referencing the `Supplier`s above here is
	/// safe regardless of registration order.
	static MenuType<KubeUIRecipeGridMenu> menuTypeFor(String kind) {
		return switch (kind) {
			case "smelting" -> FURNACE.get();
			case "blasting" -> BLAST_FURNACE.get();
			case "smoking" -> SMOKER.get();
			case "stonecutting" -> STONECUTTER.get();
			case "smithing" -> SMITHING.get();
			default -> CRAFTING.get();
		};
	}

	/// The "define one trade" GUI behind `/kubeui trader-designer`'s "Add Trade" button - see
	/// [KubeUITraderGridMenu].
	static final Supplier<MenuType<KubeUITraderGridMenu>> TRADER_GRID = MENUS.register(
		"trader_grid", () -> new MenuType<>(KubeUITraderGridMenu::new, FeatureFlags.VANILLA_SET)
	);

	/// The real trading GUI opened when right-clicking a tagged trader - see
	/// [KubeUITradeExecuteMenu].
	static final Supplier<MenuType<KubeUITradeExecuteMenu>> TRADE_EXECUTE = MENUS.register(
		"trade_execute", () -> new MenuType<>(KubeUITradeExecuteMenu::new, FeatureFlags.VANILLA_SET)
	);

	public static void register(IEventBus modEventBus) {
		MENUS.register(modEventBus);
	}
}
