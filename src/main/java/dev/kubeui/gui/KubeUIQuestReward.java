package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;

/// One reward granted on completing a [KubeUIQuestDef] - `type` is `"item"` (`data.item`/
/// `data.count`), `"xp"` (`data.levels`, via the real `Player#giveExperienceLevels`), or
/// `"command"` (`data.command`, run with an elevated permission source but the completing player
/// as its entity, so `@s` resolves to them - see [KubeUIQuests#grantRewards]). A first-class
/// currency reward type is deliberately not included yet - the roadmap's own economy system
/// (`KubeUI.currency(...)`) doesn't exist in this codebase yet, so there's nothing real to grant;
/// a script that wants that today can use a `"command"` reward against its own economy mechanism.
record KubeUIQuestReward(
	String type,
	CompoundTag data
) {
}
