package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/// Client-side half of `KubeUI.currencyHistory(currency)`/`.leaderboard(currency)`/`.shop(shopId)`
/// - sends the request ([KubeUICurrency]/[KubeUIShop] do the actual lookup,
/// server-side) and builds the resulting screen once it replies, same "ask the server, open once
/// it replies" shape as [KubeUIRecipeBridge]. Buy/sell re-sends the *whole* shop screen afterward
/// (a fresh `SHOP_RESULT_SCREEN_ID` reply from [KubeUINetworking], same "mutate then re-send"
/// convention [KubeUITraderDesigner]'s actions already use) rather than patching just the one
/// changed line in place - simpler, and correct even when a fluctuating price changed a *different*
/// line since the screen was last built.
final class KubeUIEconomyBridge {
	private KubeUIEconomyBridge() {
	}

	static void requestHistory(String currency) {
		var data = new CompoundTag();
		data.putString("currency", currency == null ? "" : currency);
		KubeUINetworking.sendAction(KubeUIActions.CURRENCY_HISTORY_ACTION, data);
	}

	static void requestLeaderboard(String currency) {
		var data = new CompoundTag();
		data.putString("currency", currency);
		KubeUINetworking.sendAction(KubeUIActions.LEADERBOARD_ACTION, data);
	}

	static void requestShop(String shopId) {
		var data = new CompoundTag();
		data.putString("shopId", shopId);
		KubeUINetworking.sendAction(KubeUIActions.SHOP_OPEN_ACTION, data);
	}

	static void receiveHistory(ListTag entries) {
		var builder = KubeUIScreenBuilder.builder("Transaction History").draggable().elementSize(320, 20);

		if (entries.isEmpty()) {
			builder.label("empty", "No transactions yet.");
		} else {
			builder.scrollPanel(260, panel -> {
				int index = 0;
				for (var entryTag : entries) {
					if (entryTag instanceof CompoundTag tag) {
						appendHistoryRow(panel, tag, index++);
					}
				}
			});
		}

		builder.divider();
		builder.button("Close", ctx -> ctx.close());
		builder.open();
	}

	private static void appendHistoryRow(KubeUIScreenBuilder panel, CompoundTag tag, int index) {
		String currency = tag.getStringOr("currency", "");
		long delta = tag.getLongOr("delta", 0);
		long balance = tag.getLongOr("balance", 0);
		String type = tag.getStringOr("type", "");
		String sign = delta >= 0 ? "+" : "";
		String time = formatTime(tag.getLongOr("time", 0));
		panel.label("hist_" + index, time + "  " + sign + delta + " " + currency + " (" + type + ", balance " + balance + ")");
	}

	private static String formatTime(long epochMillis) {
		return epochMillis <= 0 ? "" : new SimpleDateFormat("MM-dd HH:mm").format(new Date(epochMillis));
	}

	static void receiveLeaderboard(CompoundTag data) {
		String currency = data.getStringOr("currency", "");
		var rows = new ArrayList<List<String>>();
		int rank = 1;
		for (var entryTag : data.getListOrEmpty("entries")) {
			if (entryTag instanceof CompoundTag tag) {
				rows.add(List.of(String.valueOf(rank++), tag.getStringOr("name", "?"), String.valueOf(tag.getLongOr("balance", 0))));
			}
		}

		var builder = KubeUIScreenBuilder.builder("Leaderboard - " + currency).draggable().elementSize(320, 20);
		if (rows.isEmpty()) {
			builder.label("empty", "No balances yet.");
		} else {
			builder.table("leaderboard", List.of("#", "Player", "Balance"), List.of(30, 150, 100), rows);
		}
		builder.divider();
		builder.button("Close", ctx -> ctx.close());
		builder.open();
	}

	static void receiveShop(CompoundTag data) {
		String shopId = data.getStringOr("id", "");
		String name = data.getStringOr("name", shopId);
		var stock = data.getListOrEmpty("stock");

		var builder = KubeUIScreenBuilder.builder(name).draggable().elementSize(280, 20);
		if (stock.isEmpty()) {
			builder.label("empty", "Nothing for sale.");
		} else {
			builder.scrollPanel(240, panel -> {
				for (var entryTag : stock) {
					if (entryTag instanceof CompoundTag tag) {
						appendShopRow(panel, shopId, tag);
					}
				}
			});
		}
		builder.divider();
		builder.button("Close", ctx -> ctx.close());
		builder.open();
	}

	private static void appendShopRow(KubeUIScreenBuilder builder, String shopId, CompoundTag tag) {
		String stockId = tag.getStringOr("id", "");
		String itemId = tag.getStringOr("item", "");
		int count = tag.getIntOr("count", 1);
		String currency = tag.getStringOr("currency", "");
		long price = tag.getLongOr("price", 0);
		boolean sellable = tag.getBooleanOr("sellable", false);
		int stock = tag.getIntOr("stock", -1);

		var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(itemId));

		builder.row(row -> {
			if (item != null) {
				row.item(item, count);
			}
			row.label(stockId + "_label", shortId(itemId) + " x" + count + " - " + price + " " + currency + (stock >= 0 ? " (" + stock + " left)" : "")).width(160);
			row.button("Buy", ctx -> sendShopAction(KubeUIActions.SHOP_BUY_ACTION, shopId, stockId)).width(50);
			if (sellable) {
				row.button("Sell", ctx -> sendShopAction(KubeUIActions.SHOP_SELL_ACTION, shopId, stockId)).width(50);
			}
		});
	}

	private static void sendShopAction(String action, String shopId, String stockId) {
		var data = new CompoundTag();
		data.putString("shopId", shopId);
		data.putString("stockId", stockId);
		data.putInt("qty", 1);
		KubeUINetworking.sendAction(action, data);
	}

	private static String shortId(String id) {
		int colon = id.lastIndexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}
}
