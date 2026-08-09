package dev.kubeui.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

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
						String type = KubeUINbtCompat.getStringOr(tag, "type", "");
						String item = KubeUINbtCompat.getStringOr(tag, "item", "");
						int count = KubeUINbtCompat.getIntOr(tag, "count", 0);
						String playerName = KubeUINbtCompat.getStringOr(tag, "playerName", "?");
						String time = formatTime(KubeUINbtCompat.getLongOr(tag, "time", 0));
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
		String networkId = KubeUINbtCompat.getStringOr(data, "networkId", "");
		var entries = KubeUINbtCompat.getListOrEmpty(data, "items");

		var builder = KubeUIScreenBuilder.builder("Network: " + networkId).draggable().elementSize(280, 20);
		if (KubeUINbtCompat.getBooleanOr(data, "denied", false)) {
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
		String itemId = KubeUINbtCompat.getStringOr(tag, "item", "");
		int count = KubeUINbtCompat.getIntOr(tag, "count", 0);
		var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(itemId)).orElse(null);

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
