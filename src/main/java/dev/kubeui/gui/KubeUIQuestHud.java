package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

/// Client-side cache of the periodic HUD push [KubeUIQuestEvents#pushHudUpdate] sends for
/// whichever quest is currently tracked - pure data, no Minecraft rendering dependency, same split
/// [KubeUIToast] already uses between itself (data) and `KubeUIToastRenderer` (the actual
/// `RenderGuiEvent.Post` draw call, in `dev.kubeui.plugin`).
public final class KubeUIQuestHud {
	public record Entry(String label, int progress, int target) {
	}

	private static volatile String title = null;
	private static volatile List<Entry> objectives = List.of();
	private static volatile boolean canComplete = false;

	private KubeUIQuestHud() {
	}

	static void receive(CompoundTag data) {
		String questId = data.getStringOr("questId", "");
		if (questId.isEmpty()) {
			title = null;
			objectives = List.of();
			canComplete = false;
			return;
		}

		title = data.getStringOr("title", questId);
		canComplete = data.getBooleanOr("canComplete", false);

		var entries = new ArrayList<Entry>();
		for (var tag : data.getListOrEmpty("objectives")) {
			if (tag instanceof CompoundTag objectiveTag) {
				entries.add(new Entry(
					objectiveTag.getStringOr("label", ""),
					objectiveTag.getIntOr("progress", 0),
					objectiveTag.getIntOr("target", 1)
				));
			}
		}
		objectives = List.copyOf(entries);
	}

	/// `null` when nothing is currently tracked - the renderer draws nothing in that case.
	public static String title() {
		return title;
	}

	public static List<Entry> objectives() {
		return objectives;
	}

	public static boolean canComplete() {
		return canComplete;
	}
}
