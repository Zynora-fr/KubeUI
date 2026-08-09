package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;

/// Client-side half of `KubeUI.machineNetworkStatus(networkId)` - one screen
/// doubles as the "controller" view (every linked machine's live kind/progress/energy) and
/// the stats screen (a bar chart of crafted counts per machine, the real chart widget
/// already delivered) rather than two separate screens for what's
/// fundamentally the same "status of every machine in this network" data.
final class KubeUIMachineBridge {
	private KubeUIMachineBridge() {
	}

	static void requestNetworkStatus(String networkId) {
		var data = new CompoundTag();
		data.putString("networkId", networkId);
		KubeUINetworking.sendAction(KubeUIActions.MACHINE_NETWORK_STATUS_ACTION, data);
	}

	static void receiveNetworkStatus(CompoundTag data) {
		String networkId = KubeUINbtCompat.getStringOr(data, "networkId", "");
		var entries = KubeUINbtCompat.getListOrEmpty(data, "entries");

		var builder = KubeUIScreenBuilder.builder("Machine Network: " + networkId).draggable().elementSize(280, 20);

		if (entries.isEmpty()) {
			builder.label("empty", "No machines currently loaded on this network.");
		} else {
			var craftedValues = new ArrayList<Double>();
			var labels = new ArrayList<String>();
			int index = 0;
			for (var entry : entries) {
				if (entry instanceof CompoundTag tag) {
					String kind = KubeUINbtCompat.getStringOr(tag, "kind", "?");
					int progress = KubeUINbtCompat.getIntOr(tag, "progress", 0);
					int processTicks = Math.max(1, KubeUINbtCompat.getIntOr(tag, "processTicks", 1));
					int energy = KubeUINbtCompat.getIntOr(tag, "energy", 0);
					int maxEnergy = Math.max(1, KubeUINbtCompat.getIntOr(tag, "maxEnergy", 1));
					long crafted = KubeUINbtCompat.getLongOr(tag, "crafted", 0);

					builder.label("machine_" + index, kind + ": " + progress + "/" + processTicks + " progress, "
						+ energy + "/" + maxEnergy + " energy, " + crafted + " crafted");

					craftedValues.add((double) crafted);
					labels.add(kind + " #" + index);
					index++;
				}
			}
			builder.divider();
			builder.chart("craftedChart", "bar", craftedValues, labels);
		}

		builder.divider();
		builder.button("Close", ctx -> ctx.close());
		builder.open();
	}
}
