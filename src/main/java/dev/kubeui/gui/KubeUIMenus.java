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

	/// [KubeUIStorageBlockEntity]'s menu - real vanilla `ChestMenu` reused directly (see that
	/// class's `createMenu`), just under KubeUI's own `MenuType` so [KubeUIStorageScreen] (not
	/// vanilla's shared `ContainerScreen`) is what opens for it. The bare 2-arg factory (client's
	/// own local reconstruction from `ClientboundOpenScreenPacket`, same convention as every other
	/// entry here) needs a real, correctly-sized transient `Container` - a plain `SimpleContainer`,
	/// exactly what vanilla's own `ChestMenu.threeRows(...)` does for the same purpose.
	static final Supplier<MenuType<net.minecraft.world.inventory.ChestMenu>> STORAGE = MENUS.register("storage_crate", KubeUIMenus::createStorageMenuType);

	/// Split out from [#STORAGE]'s own initializer - the real `ChestMenu` constructor needs the
	/// `MenuType<?>` it's being registered under as one of its own arguments, and `javac` rejects a
	/// field referencing itself directly inside its own initializer expression (even inside a
	/// lambda) as a "self-reference in initializer" error. A separate method has no such
	/// restriction - `STORAGE` is always already assigned by the time this factory lambda actually
	/// *runs* (the client reconstructing a menu from a network packet, well after start-up
	/// registration finished), so the self-reference itself is perfectly safe at runtime.
	private static MenuType<net.minecraft.world.inventory.ChestMenu> createStorageMenuType() {
		return new MenuType<>(
			(id, inv) -> new net.minecraft.world.inventory.ChestMenu(STORAGE.get(), id, inv, new net.minecraft.world.SimpleContainer(KubeUIStorageBlockEntity.SLOT_COUNT), 3),
			FeatureFlags.VANILLA_SET
		);
	}

	/// [KubeUIBackpackItem]'s menu - real vanilla `ChestMenu` reused directly (see that class's
	/// `use`), 2 rows/18 slots. Same self-referencing-factory shape as [#STORAGE] and for the same
	/// reason - see its own comment.
	static final Supplier<MenuType<net.minecraft.world.inventory.ChestMenu>> BACKPACK = MENUS.register("backpack", KubeUIMenus::createBackpackMenuType);

	private static MenuType<net.minecraft.world.inventory.ChestMenu> createBackpackMenuType() {
		return new MenuType<>(
			(id, inv) -> new net.minecraft.world.inventory.ChestMenu(BACKPACK.get(), id, inv, new net.minecraft.world.SimpleContainer(KubeUIBackpackItem.SLOTS), 2),
			FeatureFlags.VANILLA_SET
		);
	}

	/// [KubeUIMachineBlockEntity]'s menu - its own custom [KubeUIMachineMenu], not a
	/// reused vanilla one (unlike [#STORAGE]/[#BACKPACK]) since a machine's fixed input/output/
	/// upgrade slot layout at real furnace coordinates isn't something any vanilla menu already is.
	static final Supplier<MenuType<KubeUIMachineMenu>> MACHINE = MENUS.register("machine", () -> new MenuType<>(KubeUIMachineMenu::new, FeatureFlags.VANILLA_SET));

	public static void register(IEventBus modEventBus) {
		MENUS.register(modEventBus);
	}
}
