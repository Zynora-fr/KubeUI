package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;

import java.text.SimpleDateFormat;
import java.util.Date;

/// Client-side half of `KubeUI.storageHistory()`/`.storageNetworkView(networkId)` - same
/// "ask the server, open once it replies" shape as [KubeUIEconomyBridge].
final class KubeUIStorageBridge {
	private KubeUIStorageBridge() {
	}

	static void requestHistory() {
		KubeUINetworking.sendAction(KubeUIActions.STORAGE_HISTORY_REQUEST_ACTION, new CompoundTag());
	}

	static void requestNetworkView(String networkId) {
		var data = new CompoundTag();
		data.putString("networkId", networkId);
		KubeUINetworking.sendAction(KubeUIActions.STORAGE_NETWORK_VIEW_ACTION, data);
	}

	static void receiveHistory(ListTag entries) {
		var builder = KubeUIScreenBuilder.builder("Storage History").draggable().elementSize(320, 20);

		if (entries.isEmpty()) {
			builder.label("empty", "No movements recorded yet.");
		} else {
			builder.scrollPanel(260, panel -> {
				int index = 0;
				for (var entryTag : entries) {
					if (entryTag instanceof CompoundTag tag) {
						String type = tag.getStringOr("type", "");
						String item = tag.getStringOr("item", "");
						int count = tag.getIntOr("count", 0);
						String playerName = tag.getStringOr("playerName", "?");
						String time = formatTime(tag.getLongOr("time", 0));
						String verb = "deposit".equals(type) ? "deposited" : "withdrew";
						panel.label("hist_" + index++, time + "  " + playerName + " " + verb + " " + count + "x " + shortId(item));
					}
				}
			});
		}

		builder.divider();
		builder.button("Close", ctx -> ctx.close());
		builder.open();
	}

	static void receiveNetworkView(CompoundTag data) {
		String networkId = data.getStringOr("networkId", "");
		var entries = data.getListOrEmpty("items");

		var builder = KubeUIScreenBuilder.builder("Network: " + networkId).draggable().elementSize(280, 20);
		if (data.getBooleanOr("denied", false)) {
			builder.label("denied", "You don't have access to any container on this network.");
		} else if (entries.isEmpty()) {
			builder.label("empty", "Nothing stored on this network yet.");
		} else {
			builder.scrollPanel(240, panel -> {
				for (var entryTag : entries) {
					if (entryTag instanceof CompoundTag tag) {
						appendItemRow(panel, tag);
					}
				}
			});
		}
		builder.divider();
		builder.button("Close", ctx -> ctx.close());
		builder.open();
	}

	private static void appendItemRow(KubeUIScreenBuilder builder, CompoundTag tag) {
		String itemId = tag.getStringOr("item", "");
		int count = tag.getIntOr("count", 0);
		var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(itemId));

		builder.row(row -> {
			if (item != null) {
				row.item(item, Math.min(count, 99));
			}
			row.label(itemId + "_label", shortId(itemId) + ": " + count);
		});
	}

	private static String formatTime(long epochMillis) {
		return epochMillis <= 0 ? "" : new SimpleDateFormat("MM-dd HH:mm").format(new Date(epochMillis));
	}

	private static String shortId(String id) {
		int colon = id.lastIndexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}
}
