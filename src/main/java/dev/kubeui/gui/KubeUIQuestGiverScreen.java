package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/// Opens automatically when the server pushes a `QUEST_GIVER_RESULT_SCREEN_ID` reply - on right-
/// clicking an entity tagged via `KubeUIActions.tagQuestGiver(...)` (see
/// [KubeUIQuestGiverInteraction]) and again after an Accept/Turn-in click, so the screen always
/// shows the just-changed real state rather than a client guess. This is the one quest screen with
/// real Accept/Turn-in buttons - [KubeUIQuestLogScreen] is deliberately read-only.
final class KubeUIQuestGiverScreen {
	private static String giverUuid = "";
	private static String giverName = "";
	private static List<CompoundTag> quests = List.of();

	private KubeUIQuestGiverScreen() {
	}

	static void receive(CompoundTag data) {
		giverUuid = KubeUINbtCompat.getStringOr(data, "giverUuid", "");
		giverName = KubeUINbtCompat.getStringOr(data, "giverName", "Quest Giver");

		var list = new ArrayList<CompoundTag>();
		for (var entry : data.getList("quests", 10)) {
			if (entry instanceof CompoundTag tag) {
				list.add(tag);
			}
		}
		quests = list;

		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	private static KubeUIScreenBuilder buildScreen() {
		var window = Minecraft.getInstance().getWindow();
		int totalWidth = clamp((int) (window.getGuiScaledWidth() * 0.8), 340, 620);
		int listHeight = clamp((int) (window.getGuiScaledHeight() * 0.6), 140, 360);

		var b = KubeUIScreenBuilder.builder(giverName)
			.draggable()
			.elementSize(totalWidth, 20);

		KubeUIQuestTheme.apply(b);

		if (quests.isEmpty()) {
			b.label("empty", "Nothing to offer right now.");
		} else {
			b.scrollPanel(listHeight, panel -> {
				for (var quest : quests) {
					appendQuestRow(panel, quest, totalWidth);
				}
			});
		}

		b.divider();
		b.button("Close", ctx -> ctx.close());
		return b;
	}

	private static void appendQuestRow(KubeUIScreenBuilder panel, CompoundTag quest, int totalWidth) {
		String id = KubeUINbtCompat.getStringOr(quest, "id", "");
		String title = KubeUINbtCompat.getStringOr(quest, "title", id);
		String description = KubeUINbtCompat.getStringOr(quest, "description", "");
		String status = KubeUINbtCompat.getStringOr(quest, "status", "locked");

		panel.label("title_" + id, title);
		if (!description.isEmpty()) {
			panel.label("desc_" + id, description);
		}

		if ("locked".equals(status)) {
			panel.label("locked_" + id, "  Requires another quest to be completed first.");
		} else {
			for (var entry : quest.getList("objectives", 10)) {
				if (entry instanceof CompoundTag objectiveTag) {
					String label = KubeUINbtCompat.getStringOr(objectiveTag, "label", "");
					int target = KubeUINbtCompat.getIntOr(objectiveTag, "target", 1);
					if ("active".equals(status) || "readyToComplete".equals(status)) {
						int progress = KubeUINbtCompat.getIntOr(objectiveTag, "progress", 0);
						panel.label("obj_" + id + "_" + label.hashCode(), "  - " + label + ": " + progress + "/" + target);
					} else {
						panel.label("obj_" + id + "_" + label.hashCode(), "  - " + label + " (" + target + ")");
					}
				}
			}
			for (var entry : quest.getList("rewards", 10)) {
				if (entry instanceof CompoundTag rewardTag) {
					panel.label("reward_" + id + "_" + entry.hashCode(), "  Reward: " + describeReward(rewardTag));
				}
			}
		}

		panel.row(row -> {
			switch (status) {
				case "available" -> row.button("Accept", ctx -> sendGiverAction(KubeUIActions.QUEST_GIVER_ACCEPT_ACTION, id)).width(90);
				case "readyToComplete" -> row.button("Turn In", ctx -> sendGiverAction(KubeUIActions.QUEST_GIVER_COMPLETE_ACTION, id)).width(90);
				case "active" -> row.label("waiting_" + id, "  In progress...");
				case "completed" -> row.badge("Completed", 0xFF00AA00L);
				default -> row.badge("Locked", 0xFF888888L);
			}
		});
		panel.divider();
	}

	private static String describeReward(CompoundTag rewardTag) {
		return switch (KubeUINbtCompat.getStringOr(rewardTag, "type", "")) {
			case "item" -> KubeUINbtCompat.getIntOr(rewardTag, "count", 1) + "x " + shortId(KubeUINbtCompat.getStringOr(rewardTag, "item", ""));
			case "xp" -> KubeUINbtCompat.getIntOr(rewardTag, "levels", 0) + " XP level(s)";
			default -> KubeUINbtCompat.getStringOr(rewardTag, "label", "Special reward");
		};
	}

	private static String shortId(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	private static void sendGiverAction(String action, String questId) {
		var data = new CompoundTag();
		data.putString("questId", questId);
		data.putString("giverUuid", giverUuid);
		KubeUINetworking.sendAction(action, data);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
