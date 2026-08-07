package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/// The in-game Custom Trader screen (`/kubeui trader-designer`) - see [KubeUITraderDesigner] for
/// what actually happens server-side. Same static-state-plus-`refresh()` pattern already
/// established by [KubeUIRecipeDesignerScreen]/[KubeUIFileEditor].
public final class KubeUITraderDesignerScreen {
	private static List<CompoundTag> lastKnownTrades = List.of();
	private static boolean lastKnownHasAI = true;
	private static boolean lastKnownCanMove = true;

	private KubeUITraderDesignerScreen() {
	}

	public static void open() {
		var data = new CompoundTag();
		KubeUINetworking.sendAction(KubeUIActions.TRADER_DESIGNER_LIST_ACTION, data);
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	private static void refresh() {
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	static void receiveList(CompoundTag data) {
		var list = new ArrayList<CompoundTag>();
		for (var entry : data.getListOrEmpty("trades")) {
			if (entry instanceof CompoundTag tag) {
				list.add(tag);
			}
		}
		lastKnownTrades = list;
		lastKnownHasAI = data.getBooleanOr("hasAI", true);
		lastKnownCanMove = data.getBooleanOr("canMove", true);
		refresh();
	}

	private static KubeUIScreenBuilder buildScreen() {
		var window = Minecraft.getInstance().getWindow();
		int totalWidth = clamp((int) (window.getGuiScaledWidth() * 0.8), 340, 620);
		int listHeight = clamp((int) (window.getGuiScaledHeight() * 0.3), 80, 200);

		var b = KubeUIScreenBuilder.builder("Custom Trader")
			.draggable()
			.elementSize(totalWidth, 20)
			.windowBackground(KubeUIPanelTextures.TRADER_DESIGNER);

		b.label("hint", "Build a trader by adding trades, then give yourself the egg - right-click a block to spawn it.");

		b.row(row -> {
			row.toggle("hasAI", "Has AI", lastKnownHasAI, (ctx, value) -> setFlags(value, lastKnownCanMove)).width(totalWidth / 2 - 5);
			row.toggle("canMove", "Can move", lastKnownCanMove, (ctx, value) -> setFlags(lastKnownHasAI, value)).width(totalWidth / 2 - 5);
		});
		b.divider();

		b.label("tradesLabel", lastKnownTrades.isEmpty() ? "No trades added yet." : lastKnownTrades.size() + " trade(s) so far:");
		if (!lastKnownTrades.isEmpty()) {
			b.scrollPanel(listHeight, panel -> {
				for (int i = 0; i < lastKnownTrades.size(); i++) {
					int index = i;
					var tag = lastKnownTrades.get(i);
					panel.row(row -> {
						for (var costEntry : tag.getListOrEmpty("costs")) {
							if (costEntry instanceof CompoundTag costTag) {
								String costItem = costTag.getStringOr("item", "");
								row.recipeSlot("cost_" + index + "_" + costItem, List.of(costItem));
								row.label("costLabel_" + index + "_" + costItem, "x" + costTag.getIntOr("count", 1)).width(24);
							}
						}
						row.label("arrow_" + index, "  ->  ").width(40);
						String resultItem = tag.getStringOr("resultItem", "");
						row.recipeSlot("result_" + index, List.of(resultItem));
						row.label("resultLabel_" + index, "x" + tag.getIntOr("resultCount", 1)).width(24);
						row.button("Remove", ctx -> {
							var data = new CompoundTag();
							data.putInt("index", index);
							KubeUINetworking.sendAction(KubeUIActions.TRADER_DESIGNER_REMOVE_TRADE_ACTION, data);
						}).width(70);
					});
				}
			});
		}
		b.divider();

		b.row(row -> {
			row.button("Add Trade", ctx -> KubeUINetworking.sendAction(KubeUIActions.TRADER_DESIGNER_OPEN_GRID_ACTION, new CompoundTag())).width(totalWidth / 2 - 5);
			row.button("Give Egg", ctx -> KubeUINetworking.sendAction(KubeUIActions.TRADER_DESIGNER_GIVE_EGG_ACTION, new CompoundTag())).width(totalWidth / 2 - 5);
		});

		b.divider();
		b.button("Close", ctx -> ctx.close());
		return b;
	}

	private static void setFlags(boolean hasAI, boolean canMove) {
		var data = new CompoundTag();
		data.putBoolean("hasAI", hasAI);
		data.putBoolean("canMove", canMove);
		KubeUINetworking.sendAction(KubeUIActions.TRADER_DESIGNER_SET_FLAGS_ACTION, data);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
