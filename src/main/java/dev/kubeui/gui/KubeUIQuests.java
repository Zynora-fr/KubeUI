package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// The quest engine backing `KubeUIActions.defineQuest(...)`/`/kubeui quest-editor`/
/// `/kubeui quest-log`/quest-giver entities. Same overall shape as [KubeUIVillagerTrades] (a
/// static in-memory registry of definitions, plus per-player progress on real, disk-persisted
/// entity NBT) with one addition: a quest defined through the in-game editor has no script to
/// re-register it on the next boot, so [#DEFS] alone isn't enough for those - see
/// [#loadEditorQuests]/[#saveEditorQuests] for the second, file-backed storage tier that gives
/// editor-made quests the same restart survival a script-defined quest gets for free. This mirrors
/// the exact lesson learned from a real, reported bug in the trading system: an egg-spawned
/// trader's trades lived only in an in-memory pool and vanished on every server restart until they
/// were moved onto the entity's own persistent data instead.
///
/// Objective progress is intentionally **not** cached anywhere - every call to
/// [#objectiveProgress] recomputes it from the actual current game state (inventory contents, XP
/// level) or a stored counter, both re-read fresh every time. This avoids an entire class of
/// staleness bugs (a cached number surviving past the state it described) at the cost of a little
/// redundant computation - a fine trade for something checked at most a few times per second per
/// player (quest log open, HUD tick, turn-in attempt), never in a hot per-tick loop for its own
/// sake.
final class KubeUIQuests {
	private static final String PROGRESS_TAG_KEY = "kubeui_quest_progress";
	private static final String STATE_ACTIVE = "active";
	private static final String STATE_COMPLETED = "completed";

	private static final Map<String, KubeUIQuestDef> DEFS = new ConcurrentHashMap<>();
	private static final Map<String, KubeUIQuestDef> EDITOR_DEFS = new ConcurrentHashMap<>();

	private KubeUIQuests() {
	}

	// ---------------------------------------------------------------- definitions

	static void defineQuest(KubeUIQuestDef def) {
		DEFS.put(def.id(), def);
	}

	static KubeUIQuestDef get(String id) {
		return DEFS.get(id);
	}

	static Collection<KubeUIQuestDef> all() {
		return DEFS.values();
	}

	static void defineEditorQuest(MinecraftServer server, KubeUIQuestDef def) {
		var withSource = new KubeUIQuestDef(def.id(), def.title(), def.description(), def.requires(), def.objectives(), def.rewards(), "editor");
		EDITOR_DEFS.put(withSource.id(), withSource);
		DEFS.put(withSource.id(), withSource);
		saveEditorQuests(server);
	}

	static void deleteEditorQuest(MinecraftServer server, String id) {
		if (EDITOR_DEFS.remove(id) != null) {
			DEFS.remove(id);
			saveEditorQuests(server);
		}
	}

	static Collection<KubeUIQuestDef> editorQuests() {
		return EDITOR_DEFS.values();
	}

	// ---------------------------------------------------------------- per-player progress storage

	private static CompoundTag progressRoot(ServerPlayer player) {
		var root = player.getPersistentData();
		return KubeUINbtCompat.getCompoundOrCreate(root, PROGRESS_TAG_KEY);
	}

	private static CompoundTag questEntry(ServerPlayer player, String questId) {
		var root = progressRoot(player);
		return KubeUINbtCompat.getCompoundOrCreate(root, questId);
	}

	private static CompoundTag counters(ServerPlayer player, String questId) {
		var entry = questEntry(player, questId);
		return KubeUINbtCompat.getCompoundOrCreate(entry, "counters");
	}

	private static CompoundTag visited(ServerPlayer player, String questId) {
		var entry = questEntry(player, questId);
		return KubeUINbtCompat.getCompoundOrCreate(entry, "visited");
	}

	/// `""` (never started), `"active"`, or `"completed"`.
	static String state(ServerPlayer player, String questId) {
		return KubeUINbtCompat.getStringOr(progressRoot(player).getCompound(questId), "state", "");
	}

	static boolean prerequisitesMet(ServerPlayer player, KubeUIQuestDef def) {
		for (var requiredId : def.requires()) {
			if (!STATE_COMPLETED.equals(state(player, requiredId))) {
				return false;
			}
		}
		return true;
	}

	static boolean canAccept(ServerPlayer player, String questId) {
		var def = DEFS.get(questId);
		return def != null && state(player, questId).isEmpty() && prerequisitesMet(player, def);
	}

	static boolean accept(ServerPlayer player, String questId) {
		if (!canAccept(player, questId)) {
			return false;
		}
		questEntry(player, questId).putString("state", STATE_ACTIVE);
		return true;
	}

	// ---------------------------------------------------------------- objective progress

	/// `data.item` count currently held across the player's own inventory (main + hotbar, not
	/// armor/offhand - a "collect" objective is about what you're carrying to turn in, not what
	/// you happen to be wearing).
	private static int countHeldItems(ServerPlayer player, String itemId) {
		var item = resolveItem(itemId);
		if (item == null) {
			return 0;
		}
		int total = 0;
		for (var stack : player.getInventory().items) {
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static int counter(ServerPlayer player, String questId, String objectiveId) {
		return KubeUINbtCompat.getIntOr(counters(player, questId), objectiveId, 0);
	}

	/// Bumps a generic, stored objective counter (used by both the built-in `"kill"` type, via
	/// [KubeUIQuestEvents], and any script-defined custom type via
	/// `KubeUIActions.incrementQuestObjective(...)`) - a no-op if the quest isn't currently
	/// `"active"` for this player, so grinding after turn-in (or before ever accepting) can't build
	/// up a stray counter that then instantly completes the objective the moment it's re-accepted.
	static void incrementObjectiveCounter(ServerPlayer player, String questId, String objectiveId, int amount) {
		if (!STATE_ACTIVE.equals(state(player, questId))) {
			return;
		}
		var counters = counters(player, questId);
		counters.putInt(objectiveId, KubeUINbtCompat.getIntOr(counters, objectiveId, 0) + amount);
	}

	static boolean isVisited(ServerPlayer player, String questId, String objectiveId) {
		return KubeUINbtCompat.getBooleanOr(visited(player, questId), objectiveId, false);
	}

	static void markVisited(ServerPlayer player, String questId, String objectiveId) {
		visited(player, questId).putBoolean(objectiveId, true);
	}

	/// The real, live-checked position/structure test behind a `"visit"` objective - called only
	/// from [KubeUIQuestEvents]'s throttled per-player tick check, only while the objective isn't
	/// already marked visited. A structure tag (e.g. `"minecraft:village"`) takes priority over an
	/// explicit position if both are somehow present; most objectives will only ever set one.
	static boolean checkVisit(ServerPlayer player, KubeUIQuestObjective objective) {
		var data = objective.data();
		String structureTagId = KubeUINbtCompat.getStringOr(data, "structureTag", "");
		if (!structureTagId.isEmpty()) {
			var identifier = ResourceLocation.tryParse(structureTagId);
			if (identifier == null || !(player.level() instanceof ServerLevel serverLevel)) {
				return false;
			}
			TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, identifier);
			return serverLevel.structureManager().getStructureWithPieceAt(player.blockPosition(), tag).isValid();
		}

		String dimensionId = KubeUINbtCompat.getStringOr(data, "dimension", "");
		if (!dimensionId.isEmpty()) {
			var identifier = ResourceLocation.tryParse(dimensionId);
			if (identifier == null) {
				return false;
			}
			var dimensionKey = ResourceKey.create(Registries.DIMENSION, identifier);
			if (player.level().dimension() != dimensionKey) {
				return false;
			}
		}

		double x = KubeUINbtCompat.getDoubleOr(data, "x", 0.0);
		double y = KubeUINbtCompat.getDoubleOr(data, "y", 0.0);
		double z = KubeUINbtCompat.getDoubleOr(data, "z", 0.0);
		double radius = Math.max(1.0, KubeUINbtCompat.getDoubleOr(data, "radius", 5.0));
		return player.distanceToSqr(x, y, z) <= radius * radius;
	}

	/// The single source of truth for "how far along is this objective" - always freshly computed,
	/// clamped to `[0, target]` regardless of type so callers never need their own bounds-checking.
	static int objectiveProgress(ServerPlayer player, KubeUIQuestDef def, KubeUIQuestObjective objective) {
		int raw = switch (objective.type()) {
			case "collect" -> countHeldItems(player, KubeUINbtCompat.getStringOr(objective.data(), "item", ""));
			case "xpLevel" -> player.experienceLevel;
			case "visit" -> isVisited(player, def.id(), objective.id()) ? objective.target() : 0;
			default -> counter(player, def.id(), objective.id());
		};
		return Math.max(0, Math.min(raw, objective.target()));
	}

	static boolean isObjectiveComplete(ServerPlayer player, KubeUIQuestDef def, KubeUIQuestObjective objective) {
		return objectiveProgress(player, def, objective) >= objective.target();
	}

	static boolean allObjectivesComplete(ServerPlayer player, KubeUIQuestDef def) {
		for (var objective : def.objectives()) {
			if (!isObjectiveComplete(player, def, objective)) {
				return false;
			}
		}
		return true;
	}

	static boolean canComplete(ServerPlayer player, String questId) {
		var def = DEFS.get(questId);
		return def != null && STATE_ACTIVE.equals(state(player, questId)) && allObjectivesComplete(player, def);
	}

	/// Grants rewards and flips the quest to `"completed"` - guarded by [#canComplete], which
	/// requires the current state to be exactly `"active"`, so calling this twice in a row (a
	/// double click, a resent packet, whatever) only ever grants rewards once: the second call sees
	/// `"completed"`, not `"active"`, and does nothing.
	static boolean complete(ServerPlayer player, String questId) {
		if (!canComplete(player, questId)) {
			return false;
		}
		var def = DEFS.get(questId);
		questEntry(player, questId).putString("state", STATE_COMPLETED);
		grantRewards(player, def);
		return true;
	}

	private static void grantRewards(ServerPlayer player, KubeUIQuestDef def) {
		for (var reward : def.rewards()) {
			switch (reward.type()) {
				case "item" -> giveItem(player, reward.data());
				case "xp" -> player.giveExperienceLevels(Math.max(0, KubeUINbtCompat.getIntOr(reward.data(), "levels", 0)));
				case "command" -> runCommand(player, KubeUINbtCompat.getStringOr(reward.data(), "command", ""));
				default -> KubeUI.LOGGER.warn("KubeUI: quest '{}' has a reward of unknown type '{}' - skipped", def.id(), reward.type());
			}
		}
	}

	private static void giveItem(ServerPlayer player, CompoundTag data) {
		var item = resolveItem(KubeUINbtCompat.getStringOr(data, "item", ""));
		if (item == null) {
			return;
		}
		int count = Math.max(1, KubeUINbtCompat.getIntOr(data, "count", 1));
		var stack = new ItemStack(item, count);
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/// Runs with an elevated permission source (independent of the completing player's own OP
	/// status - a reward command is authored by whoever built the quest, not the player redeeming
	/// it) but the player as the command's entity, so `@s` in the reward command resolves to them -
	/// the same "elevated permission, player-as-entity" shape `player.createCommandSourceStack()`
	/// plus `.withPermission(4)` (the real, plain-integer "owner" permission level) gives for free.
	private static void runCommand(ServerPlayer player, String command) {
		if (command.isBlank()) {
			return;
		}
		var source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
		player.level().getServer().getCommands().performPrefixedCommand(source, command);
	}

	// ---------------------------------------------------------------- item/entity id resolution

	private static Item resolveItem(String itemId) {
		var key = ResourceLocation.tryParse(itemId);
		return key != null ? BuiltInRegistries.ITEM.getOptional(key).orElse(null) : null;
	}

	static EntityType<?> resolveEntityType(String entityId) {
		var key = ResourceLocation.tryParse(entityId);
		return key != null ? BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null) : null;
	}

	// ---------------------------------------------------------------- HUD tracking (session-only, not persisted)

	/// Which quest, if any, a player wants shown in the HUD overlay - purely a display preference
	/// (see [KubeUIQuestEvents]/`KubeUIQuestHudRenderer`), never persisted (losing this on
	/// reconnect is fine - real quest *progress* is what [#PROGRESS_TAG_KEY] guarantees, not this).
	/// Cleared on logout by [KubeUIQuestEvents] so a long-running server doesn't accumulate stale
	/// entries for players who've since disconnected.
	private static final Map<java.util.UUID, String> TRACKED_QUEST = new ConcurrentHashMap<>();

	static void setTrackedQuest(ServerPlayer player, String questId) {
		if (questId == null || questId.isEmpty()) {
			TRACKED_QUEST.remove(player.getUUID());
		} else {
			TRACKED_QUEST.put(player.getUUID(), questId);
		}
	}

	static String trackedQuest(ServerPlayer player) {
		return TRACKED_QUEST.get(player.getUUID());
	}

	static void clearTrackedQuest(java.util.UUID playerId) {
		TRACKED_QUEST.remove(playerId);
	}

	// ---------------------------------------------------------------- quest-giver tagging

	private static final String QUEST_GIVER_TAG_KEY = "kubeui_quest_giver";

	static void tagQuestGiver(Entity entity, List<String> questIds) {
		var listTag = new ListTag();
		questIds.forEach(id -> listTag.add(StringTag.valueOf(id)));
		entity.getPersistentData().put(QUEST_GIVER_TAG_KEY, listTag);
	}

	static boolean isQuestGiver(Entity entity) {
		return entity.getPersistentData().contains(QUEST_GIVER_TAG_KEY);
	}

	static List<String> questGiverQuestIds(Entity entity) {
		var result = new ArrayList<String>();
		for (var tag : entity.getPersistentData().getList(QUEST_GIVER_TAG_KEY, 8)) {
			result.add(tag.getAsString());
		}
		return result;
	}

	// ---------------------------------------------------------------- NBT round trip

	static CompoundTag objectiveToTag(KubeUIQuestObjective objective) {
		var tag = new CompoundTag();
		tag.putString("id", objective.id());
		tag.putString("type", objective.type());
		tag.putString("label", objective.label());
		tag.putInt("target", objective.target());
		tag.put("data", objective.data());
		return tag;
	}

	static KubeUIQuestObjective objectiveFromTag(CompoundTag tag) {
		return new KubeUIQuestObjective(
			KubeUINbtCompat.getStringOr(tag, "id", ""),
			KubeUINbtCompat.getStringOr(tag, "type", ""),
			KubeUINbtCompat.getStringOr(tag, "label", ""),
			Math.max(1, KubeUINbtCompat.getIntOr(tag, "target", 1)),
			tag.getCompound("data")
		);
	}

	static CompoundTag rewardToTag(KubeUIQuestReward reward) {
		var tag = new CompoundTag();
		tag.putString("type", reward.type());
		tag.put("data", reward.data());
		return tag;
	}

	static KubeUIQuestReward rewardFromTag(CompoundTag tag) {
		return new KubeUIQuestReward(KubeUINbtCompat.getStringOr(tag, "type", ""), tag.getCompound("data"));
	}

	static CompoundTag questToTag(KubeUIQuestDef def) {
		var tag = new CompoundTag();
		tag.putString("id", def.id());
		tag.putString("title", def.title());
		tag.putString("description", def.description());

		var requires = new ListTag();
		def.requires().forEach(id -> requires.add(StringTag.valueOf(id)));
		tag.put("requires", requires);

		var objectives = new ListTag();
		def.objectives().forEach(o -> objectives.add(objectiveToTag(o)));
		tag.put("objectives", objectives);

		var rewards = new ListTag();
		def.rewards().forEach(r -> rewards.add(rewardToTag(r)));
		tag.put("rewards", rewards);

		tag.putString("source", def.source());
		return tag;
	}

	static KubeUIQuestDef questFromTag(CompoundTag tag) {
		var requires = new ArrayList<String>();
		for (var entry : tag.getList("requires", 8)) {
			requires.add(entry.getAsString());
		}

		var objectives = new ArrayList<KubeUIQuestObjective>();
		for (var entry : tag.getList("objectives", 10)) {
			if (entry instanceof CompoundTag objectiveTag) {
				objectives.add(objectiveFromTag(objectiveTag));
			}
		}

		var rewards = new ArrayList<KubeUIQuestReward>();
		for (var entry : tag.getList("rewards", 10)) {
			if (entry instanceof CompoundTag rewardTag) {
				rewards.add(rewardFromTag(rewardTag));
			}
		}

		return new KubeUIQuestDef(
			KubeUINbtCompat.getStringOr(tag, "id", ""),
			KubeUINbtCompat.getStringOr(tag, "title", ""),
			KubeUINbtCompat.getStringOr(tag, "description", ""),
			requires,
			objectives,
			rewards,
			KubeUINbtCompat.getStringOr(tag, "source", "editor")
		);
	}

	// ---------------------------------------------------------------- editor-quest file persistence

	/// Real NBT file I/O (`NbtIo.writeCompressed`/`readCompressed`) under `<world>/data/`, the same
	/// directory vanilla's own `SavedData` files already live in - deliberately *not* using this
	/// Minecraft version's newer `SavedDataType`/codec-based `SavedData` system, which would need a
	/// full `Codec` written for the whole quest-def shape (objectives/rewards/opaque `data` tags)
	/// for no real benefit here: this file is never read by anything except [KubeUIQuests] itself,
	/// so a plain compressed NBT blob is simpler and just as real/durable as going through that API.
	private static final LevelResource DATA_DIR = new LevelResource("data");

	private static Path questFile(MinecraftServer server) {
		return server.getWorldPath(DATA_DIR).resolve("kubeui_quests.dat");
	}

	static void saveEditorQuests(MinecraftServer server) {
		var root = new CompoundTag();
		var listTag = new ListTag();
		EDITOR_DEFS.values().forEach(def -> listTag.add(questToTag(def)));
		root.put("quests", listTag);

		try {
			var file = questFile(server);
			Files.createDirectories(file.getParent());
			NbtIo.writeCompressed(root, file);
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save quest-editor quests to disk", ex);
		}
	}

	/// Called once from [KubeUIQuestEvents] on `ServerAboutToStartEvent` - loads whatever the quest
	/// editor previously saved and merges it into [#DEFS], the same registry a script's
	/// `defineQuest(...)` calls populate (scripts run their own registration independently on the
	/// same boot; whichever registers a given id last simply wins, exactly like two script pools
	/// sharing an id already would).
	static void loadEditorQuests(MinecraftServer server) {
		EDITOR_DEFS.clear();
		var file = questFile(server);
		if (!Files.exists(file)) {
			return;
		}

		try {
			var root = NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			for (var entry : root.getList("quests", 10)) {
				if (entry instanceof CompoundTag questTag) {
					var def = questFromTag(questTag);
					EDITOR_DEFS.put(def.id(), def);
					DEFS.put(def.id(), def);
				}
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to load quest-editor quests from disk", ex);
		}
	}
}
