package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

/// One objective within a [KubeUIQuestDef] - `id` is unique *within its quest* (used as the
/// progress-storage key, like [KubeUITradeDef]'s `id` is unique within its pool). `type` is one of
/// the four built-ins [KubeUIQuests] understands directly (`"collect"`, `"kill"`, `"visit"`,
/// `"xpLevel"`) or anything else, treated as a generic counter a script bumps itself via
/// `KubeUIActions.incrementQuestObjective(...)` - see [KubeUIQuests#objectiveProgress] for exactly
/// how each type resolves to a progress number. `data` holds whatever extra, type-specific
/// parameters that type needs (e.g. `item`/`entity`/`dimension`+`x`+`y`+`z`+`radius`/
/// `structureTag`) - a single opaque tag rather than a dozen mostly-unused fields, the same
/// trade-off [KubeUIQuestReward] makes for the same reason.
record KubeUIQuestObjective(
	String id,
	String type,
	String label,
	int target,
	CompoundTag data
) {
}
