package dev.kubeui.gui;

import java.util.List;

/// A quest, defined either by a script (`KubeUIActions.defineQuest(id, {...})`, re-registered
/// every server boot, exactly like [KubeUITradeDef] pools) or by the in-game quest editor
/// (`/kubeui quest-editor`, baked to a real file under the world save directory - see
/// [KubeUIQuests#loadEditorQuests] - since, unlike a script, nothing re-registers it on the next
/// boot otherwise; the exact same restart-survival trap the trader egg hit before it was fixed to
/// bake trades onto the entity itself instead of an in-memory-only pool).
///
/// `requires` are other quest ids that must be in the `"completed"` state before this one can be
/// accepted - see [KubeUIQuests#prerequisitesMet]. `source` is `"script"` or `"editor"`, purely
/// bookkeeping for [KubeUIQuests] to know which ones to persist back to disk on a change; it's
/// never shown to a player.
record KubeUIQuestDef(
	String id,
	String title,
	String description,
	List<String> requires,
	List<KubeUIQuestObjective> objectives,
	List<KubeUIQuestReward> rewards,
	String source
) {
}
