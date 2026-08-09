package dev.kubeui.gui;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Backs `KubeUIActions.defineSkillTree(...)`/`.grantSkillPoints`/`.unlockSkillNode`/`.respecSkillTree`
/// - definitions are an in-memory registry re-declared every server
/// boot (same convention as [KubeUIDialogue]/[KubeUIVillagerTrades] - no editor UI was asked for
/// here either), per-player progress (points/unlocked nodes/chosen class) lives on the player's own
/// persistent data like [KubeUIActions#playerData].
final class KubeUISkills {
	private static final String PROGRESS_TAG_KEY = "kubeui_skills";

	private static final Map<String, SkillTreeDef> DEFS = new ConcurrentHashMap<>();

	private KubeUISkills() {
	}

	// ---------------------------------------------------------------- definitions

	record SkillEffect(String attributeId, double amount, String operation) {
	}

	/// `icon` is a raw script-supplied string, resolved client-side ([KubeUISkillBridge]) exactly
	/// like a dialogue node's `portrait` already is: a real registered item id shows that item's
	/// icon, anything else is used directly as a texture `Identifier` (`.image(...)`) - one script
	/// field covers both "an item icon" and "a genuinely custom texture", no separate flag needed.
	record SkillNode(String id, String name, int cost, List<String> requires, List<SkillEffect> effects, String classGroup, String icon) {
	}

	record SkillTreeDef(String id, Map<String, SkillNode> nodes, double respecCostMultiplier, String respecCurrency, int pointsPerLevel) {
	}

	static void define(String treeId, Map<String, Object> nodesRaw, double respecCostMultiplier, String respecCurrency, int pointsPerLevel) {
		var nodes = new LinkedHashMap<String, SkillNode>();
		nodesRaw.forEach((nodeId, value) -> {
			if (value instanceof Map<?, ?> map) {
				nodes.put(nodeId, parseNode(nodeId, map));
			}
		});
		DEFS.put(treeId, new SkillTreeDef(treeId, nodes, respecCostMultiplier, respecCurrency, pointsPerLevel));
	}

	/// Every registered tree's own known ids, for [KubeUISkillEvents]' level-up hook to iterate.
	static java.util.Collection<SkillTreeDef> all() {
		return DEFS.values();
	}

	static SkillTreeDef get(String treeId) {
		return DEFS.get(treeId);
	}

	private static SkillNode parseNode(String nodeId, Map<?, ?> map) {
		String name = map.get("name") instanceof String s ? s : nodeId;
		int cost = map.get("cost") instanceof Number n ? Math.max(1, n.intValue()) : 1;
		String classGroup = map.get("classGroup") instanceof String s ? s : "";
		String icon = map.get("icon") instanceof String s ? s : "";

		var requires = new ArrayList<String>();
		if (map.get("requires") instanceof List<?> list) {
			for (var entry : list) {
				requires.add(String.valueOf(entry));
			}
		}

		var effects = new ArrayList<SkillEffect>();
		if (map.get("effects") instanceof List<?> list) {
			for (var entry : list) {
				if (entry instanceof Map<?, ?> effectMap) {
					String attributeId = String.valueOf(effectMap.get("attribute"));
					double amount = effectMap.get("amount") instanceof Number n ? n.doubleValue() : 0;
					String operation = effectMap.get("operation") instanceof String s ? s : "add_value";
					effects.add(new SkillEffect(attributeId, amount, operation));
				}
			}
		}

		return new SkillNode(nodeId, name, cost, requires, effects, classGroup, icon);
	}

	// ---------------------------------------------------------------- per-player progress

	private static CompoundTag progressRoot(ServerPlayer player, String treeId) {
		var root = player.getPersistentData();
		var skillsRoot = root.getCompound(PROGRESS_TAG_KEY).orElseGet(() -> {
			var created = new CompoundTag();
			root.put(PROGRESS_TAG_KEY, created);
			return created;
		});
		return skillsRoot.getCompound(treeId).orElseGet(() -> {
			var created = new CompoundTag();
			skillsRoot.put(treeId, created);
			return created;
		});
	}

	static int points(ServerPlayer player, String treeId) {
		return progressRoot(player, treeId).getIntOr("points", 0);
	}

	static void grantPoints(ServerPlayer player, String treeId, int amount) {
		if (amount == 0) {
			return;
		}
		var root = progressRoot(player, treeId);
		root.putInt("points", Math.max(0, root.getIntOr("points", 0) + amount));
	}

	static List<String> unlockedNodes(ServerPlayer player, String treeId) {
		var result = new ArrayList<String>();
		for (var tag : progressRoot(player, treeId).getListOrEmpty("unlocked")) {
			tag.asString().ifPresent(result::add);
		}
		return result;
	}

	static boolean isUnlocked(ServerPlayer player, String treeId, String nodeId) {
		return unlockedNodes(player, treeId).contains(nodeId);
	}

	static String chosenClass(ServerPlayer player, String treeId) {
		return progressRoot(player, treeId).getStringOr("class", "");
	}

	/// Atomic: fails cleanly (no mutation) if the node's already unlocked, its prerequisites
	/// aren't all unlocked, it belongs to a `classGroup` that conflicts with whichever
	/// group the player already committed to (the *first* `classGroup` node unlocked
	/// commits the player to that group; any node from a *different* non-empty group is refused
	/// from then on, until a respec), or the player can't afford `node.cost()`.
	static boolean unlockNode(ServerPlayer player, String treeId, String nodeId) {
		var def = DEFS.get(treeId);
		if (def == null) {
			return false;
		}
		var node = def.nodes().get(nodeId);
		if (node == null) {
			return false;
		}

		var unlocked = unlockedNodes(player, treeId);
		if (unlocked.contains(nodeId)) {
			return false;
		}
		if (!unlocked.containsAll(node.requires())) {
			return false;
		}

		String committedClass = chosenClass(player, treeId);
		if (!node.classGroup().isEmpty()) {
			if (!committedClass.isEmpty() && !committedClass.equals(node.classGroup())) {
				return false;
			}
		}

		int current = points(player, treeId);
		if (current < node.cost()) {
			return false;
		}

		var root = progressRoot(player, treeId);
		root.putInt("points", current - node.cost());
		var listTag = root.getListOrEmpty("unlocked");
		var newList = new ListTag();
		newList.addAll(listTag);
		newList.add(StringTag.valueOf(nodeId));
		root.put("unlocked", newList);
		if (!node.classGroup().isEmpty() && committedClass.isEmpty()) {
			root.putString("class", node.classGroup());
		}

		applyEffects(player, node, true);
		return true;
	}

	/// Refunds every spent point and removes every applied effect - `costCurrency`/`costAmount`
	/// (both optional, from the tree's own definition) are charged first via [KubeUICurrency#charge]
	/// if configured; a `null`/empty `respecCurrency` means a free respec ("payante ou
	/// limitée" - the real, working option is the paid one; a time-limited free respec
	/// would need its own persisted cooldown timestamp, not built here to keep this one real
	/// mechanism simple rather than two half-built ones).
	static boolean respec(ServerPlayer player, String treeId) {
		var def = DEFS.get(treeId);
		if (def == null) {
			return false;
		}

		var unlocked = unlockedNodes(player, treeId);
		if (unlocked.isEmpty()) {
			return false;
		}

		if (!def.respecCurrency().isEmpty()) {
			long cost = Math.round(unlocked.size() * def.respecCostMultiplier());
			if (!KubeUICurrency.charge(player, def.respecCurrency(), Math.max(1, cost))) {
				return false;
			}
		}

		int refund = 0;
		for (var nodeId : unlocked) {
			var node = def.nodes().get(nodeId);
			if (node != null) {
				refund += node.cost();
				applyEffects(player, node, false);
			}
		}

		var root = progressRoot(player, treeId);
		root.put("unlocked", new ListTag());
		root.putString("class", "");
		root.putInt("points", points(player, treeId) + refund);
		return true;
	}

	/// Called once from [KubeUISkillEvents] on login - real permanent `AttributeModifier`s already
	/// persist on the entity itself between sessions (vanilla behavior), but `addOrReplacePermanentModifier`
	/// is idempotent (replaces by id rather than stacking), so unconditionally reapplying every
	/// already-unlocked node's effects here is a safe, cheap way to guarantee they're actually
	/// present even after e.g. a manual attribute reset, not just to *add* new ones.
	static void reapplyAll(ServerPlayer player) {
		for (var def : DEFS.values()) {
			for (var nodeId : unlockedNodes(player, def.id())) {
				var node = def.nodes().get(nodeId);
				if (node != null) {
					applyEffects(player, node, true);
				}
			}
		}
	}

	private static void applyEffects(ServerPlayer player, SkillNode node, boolean apply) {
		for (var effect : node.effects()) {
			var holder = resolveAttribute(effect.attributeId());
			if (holder == null) {
				continue;
			}
			var instance = player.getAttribute(holder);
			if (instance == null) {
				continue;
			}
			var modifierId = Identifier.fromNamespaceAndPath("kubeui", "skill_" + node.id() + "_" + effect.attributeId().replace(':', '_').replace('.', '_'));
			if (apply) {
				instance.addOrReplacePermanentModifier(new AttributeModifier(modifierId, effect.amount(), operationOf(effect.operation())));
			} else {
				instance.removeModifier(modifierId);
			}
		}
	}

	private static AttributeModifier.Operation operationOf(String name) {
		return switch (name) {
			case "add_multiplied_base" -> AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
			case "add_multiplied_total" -> AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
			default -> AttributeModifier.Operation.ADD_VALUE;
		};
	}

	private static Holder<Attribute> resolveAttribute(String id) {
		var key = Identifier.tryParse(id);
		if (key == null) {
			return null;
		}
		return BuiltInRegistries.ATTRIBUTE.get(key).orElse(null);
	}
}
