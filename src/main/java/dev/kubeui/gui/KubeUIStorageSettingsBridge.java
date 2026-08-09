package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

/// Client-side settings form for whatever storage screen is currently open - filters,
/// locking/authorized players, network id, and the movement history
/// shortcut. A write-only form (submits changes, doesn't first fetch/display the
/// storage's *current* filter/lock/network state) rather than a live-reflecting settings panel -
/// simpler, and a player can always reopen it to double-check a change landed.
final class KubeUIStorageSettingsBridge {
	private KubeUIStorageSettingsBridge() {
	}

	static void open() {
		KubeUIScreenBuilder.builder("Storage Settings")
			.elementSize(260, 20)
			.label("filterInfo", "Filter (comma-separated item ids, e.g. minecraft:cobblestone):")
			.textField("filterItems", "", "minecraft:cobblestone,minecraft:dirt", (screen, value) -> {
			})
			.row(row -> row
				.button("Whitelist", ctx -> sendFilter(ctx, "whitelist"))
				.button("Blacklist", ctx -> sendFilter(ctx, "blacklist"))
				.button("Clear", ctx -> sendFilter(ctx, "none"))
			)
			.divider()
			.button("Toggle Lock", ctx -> KubeUINetworking.sendAction(KubeUIActions.STORAGE_TOGGLE_LOCK_ACTION, new CompoundTag()))
			.label("authInfo", "Authorize/revoke a player by name (owner only):")
			.textField("authName", "", "PlayerName", (screen, value) -> {
			})
			.row(row -> row
				.button("Authorize", ctx -> sendAuthorized(ctx, KubeUIActions.STORAGE_ADD_AUTHORIZED_ACTION))
				.button("Revoke", ctx -> sendAuthorized(ctx, KubeUIActions.STORAGE_REMOVE_AUTHORIZED_ACTION))
			)
			.divider()
			.label("networkInfo", "Network id (link this storage to a named network, empty to unlink):")
			.textField("networkId", "", "guild_storage", (screen, value) -> {
			})
			.button("Set Network", ctx -> {
				var data = new CompoundTag();
				data.putString("networkId", ctx.getTextFieldValue("networkId"));
				KubeUINetworking.sendAction(KubeUIActions.STORAGE_SET_NETWORK_ACTION, data);
			})
			.divider()
			.button("View History", ctx -> {
				ctx.close();
				KubeUIScreenBuilder.storageHistory();
			})
			.button("Close", ctx -> ctx.close())
			.open();
	}

	private static void sendFilter(KubeUIContext ctx, String mode) {
		var raw = ctx.getTextFieldValue("filterItems");
		var items = new java.util.ArrayList<String>();
		if (raw != null) {
			for (var id : raw.split(",")) {
				String trimmed = id.trim();
				if (!trimmed.isEmpty()) {
					items.add(trimmed);
				}
			}
		}
		var data = new CompoundTag();
		data.putString("mode", mode);
		var listTag = new net.minecraft.nbt.ListTag();
		items.forEach(id -> listTag.add(net.minecraft.nbt.StringTag.valueOf(id)));
		data.put("items", listTag);
		KubeUINetworking.sendAction(KubeUIActions.STORAGE_SET_FILTER_ACTION, data);
	}

	private static void sendAuthorized(KubeUIContext ctx, String action) {
		String name = ctx.getTextFieldValue("authName");
		if (name == null || name.isBlank()) {
			return;
		}
		var data = new CompoundTag();
		data.putString("playerName", name.trim());
		KubeUINetworking.sendAction(action, data);
	}
}
