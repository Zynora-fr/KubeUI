package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/// `KubeUI.questLog()`/`/kubeui quest-log`/`/quest` - a read-only view of every known quest and
/// this player's progress on each, grouped by status. The screen itself has no Accept/Turn-in
/// buttons (see [KubeUIQuestGiverScreen] for those, backing a quest-giver NPC/block) - but
/// [#acceptQuest]/[#completeQuest] below back the command-line equivalent (`/quest accept`/
/// `/quest complete`) for a server that doesn't want every quest gated behind finding a specific
/// entity. Same static-state-plus-`refresh()` pattern as [KubeUITraderDesignerScreen].
public final class KubeUIQuestLogScreen {
	private static List<CompoundTag> lastKnownQuests = List.of();

	private KubeUIQuestLogScreen() {
	}

	public static void open() {
		KubeUINetworking.sendAction(KubeUIActions.QUEST_LOG_REQUEST_ACTION, new CompoundTag());
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	/// Backs `/quest accept <questId>` - lets a player start a quest without needing to find its
	/// giver entity first, for a server that wants quests reachable purely by id. Real chat
	/// feedback either way comes back from the server (see
	/// `KubeUINetworking#handleQuestCommand`) - this call itself is fire-and-forget, no screen
	/// involved.
	public static void acceptQuest(String questId) {
		var data = new CompoundTag();
		data.putString("questId", questId);
		KubeUINetworking.sendAction(KubeUIActions.QUEST_COMMAND_ACCEPT_ACTION, data);
	}

	/// Backs `/quest complete <questId>` - same as [#acceptQuest] but for turning a finished quest
	/// in, no giver entity needed.
	public static void completeQuest(String questId) {
		var data = new CompoundTag();
		data.putString("questId", questId);
		KubeUINetworking.sendAction(KubeUIActions.QUEST_COMMAND_COMPLETE_ACTION, data);
	}

	private static void refresh() {
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	static void receiveList(ListTag quests) {
		var list = new ArrayList<CompoundTag>();
		for (var entry : quests) {
			if (entry instanceof CompoundTag tag) {
				list.add(tag);
			}
		}
		lastKnownQuests = list;
		refresh();
	}

	private static KubeUIScreenBuilder buildScreen() {
		var window = Minecraft.getInstance().getWindow();
		int totalWidth = clamp((int) (window.getGuiScaledWidth() * 0.8), 340, 620);
		int listHeight = clamp((int) (window.getGuiScaledHeight() * 0.6), 140, 360);

		var b = KubeUIScreenBuilder.builder("Quest Log")
			.draggable()
			.elementSize(totalWidth, 20);

		KubeUIQuestTheme.apply(b);

		if (lastKnownQuests.isEmpty()) {
			b.label("empty", "No quests known yet.");
		} else {
			b.scrollPanel(listHeight, panel -> {
				appendGroup(panel, "readyToComplete", "Ready to turn in", totalWidth);
				appendGroup(panel, "active", "In progress", totalWidth);
				appendGroup(panel, "available", "Available", totalWidth);
				appendGroup(panel, "locked", "Locked", totalWidth);
				appendGroup(panel, "completed", "Completed", totalWidth);
			});
		}

		b.divider();
		b.button("Close", ctx -> ctx.close());
		return b;
	}

	private static void appendGroup(KubeUIScreenBuilder panel, String status, String heading, int totalWidth) {
		var inGroup = lastKnownQuests.stream().filter(q -> status.equals(q.getStringOr("status", ""))).toList();
		if (inGroup.isEmpty()) {
			return;
		}

		panel.label("heading_" + status, heading + " (" + inGroup.size() + ")");
		for (var quest : inGroup) {
			appendQuestRow(panel, quest, totalWidth);
		}
		panel.divider();
	}

	private static void appendQuestRow(KubeUIScreenBuilder panel, CompoundTag quest, int totalWidth) {
		String id = quest.getStringOr("id", "");
		String title = quest.getStringOr("title", id);
		String description = quest.getStringOr("description", "");
		String status = quest.getStringOr("status", "");

		panel.row(row -> {
			row.label("title_" + id, title).width(totalWidth - 110);
			row.badge(statusLabel(status), statusColor(status));
		});
		if (!description.isEmpty()) {
			panel.label("desc_" + id, description);
		}

		// A quest that isn't actually active always has its objective progress forced to 0 by the
		// server (see KubeUIQuestGiverInteraction#describeQuestForPlayer) - real, since nothing has
		// happened toward it yet, but showing "0/20" progress bars for a quest nobody has accepted
		// looks *exactly* like "I did the thing and it isn't counting" instead of "you haven't
		// started this yet". Spelling that out explicitly instead of a misleading progress bar was
		// a real, reported point of confusion.
		if ("available".equals(status)) {
			panel.label("notStarted_" + id, "  Not yet accepted - find this quest's giver to start it.");
		} else if ("locked".equals(status)) {
			panel.label("lockedNote_" + id, "  Requires another quest to be completed first.");
		} else {
			for (var entry : quest.getListOrEmpty("objectives")) {
				if (entry instanceof CompoundTag objectiveTag) {
					String label = objectiveTag.getStringOr("label", "");
					int progress = objectiveTag.getIntOr("progress", 0);
					int target = objectiveTag.getIntOr("target", 1);
					panel.row(row -> {
						row.label("obj_" + id + "_" + label.hashCode(), "  - " + label + ": " + progress + "/" + target).width(totalWidth - 160);
						row.progressBar("prog_" + id + "_" + label.hashCode(), progress, Math.max(1, target)).width(150);
					});
				}
			}
		}

		if ("active".equals(status) || "readyToComplete".equals(status)) {
			panel.row(row -> row.button("Track", ctx -> {
				var data = new CompoundTag();
				data.putString("questId", id);
				KubeUINetworking.sendAction(KubeUIActions.QUEST_TRACK_ACTION, data);
			}).width(80));
		}
		panel.spacer(4);
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case "readyToComplete" -> "Ready";
			case "active" -> "Active";
			case "available" -> "Available";
			case "locked" -> "Locked";
			case "completed" -> "Done";
			default -> status;
		};
	}

	private static long statusColor(String status) {
		return switch (status) {
			case "readyToComplete" -> 0xFF55FF55L;
			case "active" -> 0xFFFFD700L;
			case "available" -> 0xFF55AAFFL;
			case "locked" -> 0xFF888888L;
			case "completed" -> 0xFF00AA00L;
			default -> 0xFFFFFFFFL;
		};
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
