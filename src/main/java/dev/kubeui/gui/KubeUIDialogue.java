package dev.kubeui.gui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Backs `KubeUIActions.defineDialogue(...)`/`KubeUI.dialogue(...)`/`.tagDialogueNpc(...)` -
/// a "visual novel" node graph, re-declared every server boot the same
/// way [KubeUIQuests]' script-defined quests are (no editor UI was asked for here, so - unlike
/// quests - there's no second, file-persisted definition tier). What *is* persisted per player is
/// [#history], backed by their own persistent data like [KubeUIActions#playerData].
///
/// Every [#resolveNode]/[#choose] call is server-side and re-validates against the *live*
/// definition and the player's *current* real state - a node's text is sent once as either a
/// literal string or a `{key, fallback}` pair (resolved client-side the exact same way
/// `.labelKey(...)` already resolves one, not a second mechanism), but which *choices* a player
/// sees, and what choosing one leads to, are always decided here, never trusted from the client -
/// same posture as every other `KubeUIActions` designer/registry class already in this codebase.
final class KubeUIDialogue {
	private static final String NPC_TAG_KEY = "kubeui_dialogue_npc";
	private static final String HISTORY_TAG_KEY = "kubeui_dialogue_history";

	private static final Map<String, DialogueDef> DEFS = new ConcurrentHashMap<>();

	private KubeUIDialogue() {
	}

	// ---------------------------------------------------------------- definitions

	record TextSpec(String key, String fallback) {
	}

	record RequireSpec(String kind, String questId, String questState, String currency, long amount, String itemId, int itemCount) {
	}

	record DialogueChoice(String labelKey, String labelFallback, String next, RequireSpec requires) {
	}

	record DialogueNode(String id, String textKey, String textFallback, String portrait, String sound, int timerSeconds, String timeoutNext, List<DialogueChoice> choices) {
	}

	record DialogueDef(String id, String rootNodeId, Map<String, DialogueNode> nodes) {
	}

	static void define(String dialogueId, String rootNodeId, Map<String, Object> nodesRaw) {
		var nodes = new LinkedHashMap<String, DialogueNode>();
		nodesRaw.forEach((nodeId, value) -> {
			if (value instanceof Map<?, ?> nodeMap) {
				nodes.put(nodeId, parseNode(nodeId, nodeMap));
			}
		});
		DEFS.put(dialogueId, new DialogueDef(dialogueId, rootNodeId, nodes));
	}

	static DialogueDef get(String dialogueId) {
		return DEFS.get(dialogueId);
	}

	private static DialogueNode parseNode(String nodeId, Map<?, ?> map) {
		var text = parseTextSpec(map.get("text"));
		String portrait = map.get("portrait") instanceof String s ? s : "";
		String sound = map.get("sound") instanceof String s ? s : "";
		int timerSeconds = map.get("timerSeconds") instanceof Number n ? Math.max(0, n.intValue()) : 0;
		String timeoutNext = map.get("timeoutNext") instanceof String s && !s.isBlank() ? s : null;

		var choices = new ArrayList<DialogueChoice>();
		if (map.get("choices") instanceof List<?> choiceList) {
			for (var choiceEntry : choiceList) {
				if (choiceEntry instanceof Map<?, ?> choiceMap) {
					choices.add(parseChoice(choiceMap));
				}
			}
		}

		return new DialogueNode(nodeId, text.key(), text.fallback(), portrait, sound, timerSeconds, timeoutNext, choices);
	}

	private static DialogueChoice parseChoice(Map<?, ?> map) {
		var label = parseTextSpec(map.get("label"));
		String next = map.get("next") instanceof String s && !s.isBlank() ? s : null;
		return new DialogueChoice(label.key(), label.fallback(), next, parseRequires(map.get("requires")));
	}

	private static TextSpec parseTextSpec(Object raw) {
		if (raw instanceof String s) {
			return new TextSpec("", s);
		}
		if (raw instanceof Map<?, ?> map) {
			String key = map.get("key") instanceof String s ? s : "";
			String fallback = map.get("fallback") instanceof String s ? s : "";
			return new TextSpec(key, fallback);
		}
		return new TextSpec("", "");
	}

	private static RequireSpec parseRequires(Object raw) {
		if (!(raw instanceof Map<?, ?> map)) {
			return null;
		}
		if (map.containsKey("quest")) {
			String state = map.get("state") instanceof String s && !s.isBlank() ? s : "active";
			return new RequireSpec("quest", String.valueOf(map.get("quest")), state, null, 0, null, 0);
		}
		if (map.containsKey("currency")) {
			long amount = map.get("amount") instanceof Number n ? n.longValue() : 0;
			return new RequireSpec("currency", null, null, String.valueOf(map.get("currency")), amount, null, 0);
		}
		if (map.containsKey("item")) {
			int count = map.get("count") instanceof Number n ? Math.max(1, n.intValue()) : 1;
			return new RequireSpec("item", null, null, null, 0, String.valueOf(map.get("item")), count);
		}
		return null;
	}

	private static boolean meetsRequirement(ServerPlayer player, RequireSpec spec) {
		if (spec == null) {
			return true;
		}
		return switch (spec.kind()) {
			case "quest" -> {
				String state = KubeUIQuests.state(player, spec.questId());
				yield spec.questState().equals(state.isEmpty() ? "not_started" : state);
			}
			case "currency" -> KubeUICurrency.balance(player, spec.currency()) >= spec.amount();
			case "item" -> {
				var item = KubeUICurrency.resolveItem(spec.itemId());
				yield item != null && KubeUICurrency.countItem(player, item) >= spec.itemCount();
			}
			default -> true;
		};
	}

	// ---------------------------------------------------------------- node resolution

	/// Sends `nodeId` of `dialogueId` to `player` - only the choices whose `requires` currently
	/// passes are included (a failing one is omitted entirely, never shown disabled - see the class
	/// docs). Records `nodeId` into [#history]. Returns null (caller sends nothing) for an unknown
	/// dialogue/node id.
	static CompoundTag resolveNode(ServerPlayer player, String dialogueId, String nodeId, Entity npc) {
		var def = DEFS.get(dialogueId);
		if (def == null) {
			return null;
		}
		var node = def.nodes().get(nodeId);
		if (node == null) {
			return null;
		}

		recordHistory(player, dialogueId, nodeId);

		var reply = new CompoundTag();
		reply.putString("dialogueId", dialogueId);
		reply.putString("nodeId", nodeId);
		reply.putString("textKey", node.textKey());
		reply.putString("text", node.textFallback());
		reply.putString("sound", node.sound());
		reply.putInt("timerSeconds", node.timerSeconds());
		reply.putBoolean("ended", false);
		if (npc != null) {
			reply.putString("npcUuid", npc.getUUID().toString());
		}
		describePortrait(reply, node.portrait());

		var choicesTag = new ListTag();
		for (int index = 0; index < node.choices().size(); index++) {
			var choice = node.choices().get(index);
			if (!meetsRequirement(player, choice.requires())) {
				continue;
			}
			var choiceTag = new CompoundTag();
			choiceTag.putInt("index", index);
			choiceTag.putString("labelKey", choice.labelKey());
			choiceTag.putString("label", choice.labelFallback());
			choicesTag.add(choiceTag);
		}
		reply.put("choices", choicesTag);

		return reply;
	}

	/// Advances from `nodeId` - either the choice at `choiceIndex` (re-validated against its
	/// `requires`, rejecting a stale/tampered index the same way a schema-violating action payload
	/// is rejected elsewhere), or - if `timedOut` - straight to that node's own `timeoutNext`
	/// (no player-supplied index involved at all). A `next`/`timeoutNext` of `null`
	/// (including simply omitted) ends the dialogue. Returns null for an invalid request (unknown
	/// ids, a bad index, a `requires` that no longer passes).
	static CompoundTag choose(ServerPlayer player, String dialogueId, String nodeId, int choiceIndex, boolean timedOut, Entity npc) {
		var def = DEFS.get(dialogueId);
		if (def == null) {
			return null;
		}
		var node = def.nodes().get(nodeId);
		if (node == null) {
			return null;
		}

		String next;
		if (timedOut) {
			next = node.timeoutNext();
		} else if (choiceIndex >= 0 && choiceIndex < node.choices().size()) {
			var choice = node.choices().get(choiceIndex);
			if (!meetsRequirement(player, choice.requires())) {
				return null;
			}
			next = choice.next();
		} else {
			return null;
		}

		if (next == null) {
			var ended = new CompoundTag();
			ended.putString("dialogueId", dialogueId);
			ended.putBoolean("ended", true);
			ended.put("choices", new ListTag());
			return ended;
		}
		return resolveNode(player, dialogueId, next, npc);
	}

	private static void describePortrait(CompoundTag reply, String portraitId) {
		if (portraitId == null || portraitId.isBlank()) {
			reply.putString("portraitType", "none");
			reply.putString("portrait", "");
			return;
		}
		if (KubeUIQuests.resolveEntityType(portraitId) != null) {
			reply.putString("portraitType", "entity");
			reply.putString("portrait", portraitId);
			return;
		}
		if (KubeUICurrency.resolveItem(portraitId) != null) {
			reply.putString("portraitType", "item");
			reply.putString("portrait", portraitId);
			return;
		}
		reply.putString("portraitType", "none");
		reply.putString("portrait", "");
	}

	// ---------------------------------------------------------------- NPC tagging

	static void tagNpc(Entity entity, String dialogueId) {
		entity.getPersistentData().putString(NPC_TAG_KEY, dialogueId);
	}

	static String npcDialogueId(Entity entity) {
		return entity.getPersistentData().getStringOr(NPC_TAG_KEY, "");
	}

	// ---------------------------------------------------------------- choice history

	private static void recordHistory(ServerPlayer player, String dialogueId, String nodeId) {
		var root = player.getPersistentData();
		var historyRoot = root.getCompound(HISTORY_TAG_KEY).orElseGet(() -> {
			var created = new CompoundTag();
			root.put(HISTORY_TAG_KEY, created);
			return created;
		});

		var existing = historyRoot.getListOrEmpty(dialogueId);
		for (var tag : existing) {
			if (tag.asString().orElse("").equals(nodeId)) {
				return;
			}
		}

		var updated = new ListTag();
		updated.addAll(existing);
		updated.add(StringTag.valueOf(nodeId));
		historyRoot.put(dialogueId, updated);
	}

	/// Every node id `player` has ever reached in `dialogueId`, oldest first, deduplicated - see
	/// [KubeUIActions#dialogueHistory].
	static List<String> history(ServerPlayer player, String dialogueId) {
		var historyRoot = player.getPersistentData().getCompoundOrEmpty(HISTORY_TAG_KEY);
		var result = new ArrayList<String>();
		for (var tag : historyRoot.getListOrEmpty(dialogueId)) {
			tag.asString().ifPresent(result::add);
		}
		return result;
	}
}
