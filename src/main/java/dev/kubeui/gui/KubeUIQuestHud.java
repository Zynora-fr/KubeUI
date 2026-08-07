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
		String questId = KubeUINbtCompat.getStringOr(data, "questId", "");
		if (questId.isEmpty()) {
			title = null;
			objectives = List.of();
			canComplete = false;
			return;
		}

		title = KubeUINbtCompat.getStringOr(data, "title", questId);
		canComplete = KubeUINbtCompat.getBooleanOr(data, "canComplete", false);

		var entries = new ArrayList<Entry>();
		for (var tag : data.getList("objectives", 10)) {
			if (tag instanceof CompoundTag objectiveTag) {
				entries.add(new Entry(
					KubeUINbtCompat.getStringOr(objectiveTag, "label", ""),
					KubeUINbtCompat.getIntOr(objectiveTag, "progress", 0),
					KubeUINbtCompat.getIntOr(objectiveTag, "target", 1)
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
