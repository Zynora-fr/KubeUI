package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.List;

/// `/kubeui quest-editor` - compose a quest without writing any script, the same "concrete, more
/// modest" visual-form idea the roadmap already applied elsewhere rather than a general drag-and-
/// drop editor. Two modes: a list of quests this editor already saved (create/edit/delete), and a
/// compose form (title/description/requires + build-up-then-add objective and reward lists,
/// mirroring how [KubeUITraderDesignerScreen] builds up a trade list one captured trade at a
/// time). All the compose-form state is plain client-side fields - unlike the trader/recipe
/// designers, nothing here needs server validation until the final "Save Quest" click, since it's
/// all just text and numbers, no item-slot placement to keep server-authoritative.
public final class KubeUIQuestEditorScreen {
	private static List<CompoundTag> savedQuests = List.of();
	private static boolean composing = false;

	private static String draftId = "";
	private static String draftTitle = "";
	private static String draftDescription = "";
	private static String draftRequires = "";
	private static final List<KubeUIQuestObjective> draftObjectives = new ArrayList<>();
	private static final List<KubeUIQuestReward> draftRewards = new ArrayList<>();

	private static String pendingObjectiveType = "collect";
	private static String pendingObjectiveId = "";
	private static String pendingObjectiveLabel = "";
	private static String pendingObjectiveTarget = "1";
	private static String pendingObjectiveItemOrEntity = "";
	private static String pendingObjectiveStructureTag = "";
	private static String pendingObjectiveDimension = "minecraft:overworld";
	private static String pendingObjectiveX = "0";
	private static String pendingObjectiveY = "0";
	private static String pendingObjectiveZ = "0";
	private static String pendingObjectiveRadius = "5";

	private static String pendingRewardType = "item";
	private static String pendingRewardItem = "";
	private static String pendingRewardCount = "1";
	private static String pendingRewardLevels = "1";
	private static String pendingRewardCommand = "";

	private static final List<String> OBJECTIVE_TYPES = List.of("collect", "kill", "visit", "xpLevel", "custom");
	private static final List<String> REWARD_TYPES = List.of("item", "xp", "command");

	private KubeUIQuestEditorScreen() {
	}

	public static void open() {
		composing = false;
		KubeUINetworking.sendAction(KubeUIActions.QUEST_EDITOR_LIST_ACTION, new CompoundTag());
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
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
		savedQuests = list;
		refresh();
	}

	private static KubeUIScreenBuilder buildScreen() {
		return composing ? buildComposeScreen() : buildListScreen();
	}

	// ---------------------------------------------------------------- list mode

	private static KubeUIScreenBuilder buildListScreen() {
		var window = Minecraft.getInstance().getWindow();
		int totalWidth = clamp((int) (window.getGuiScaledWidth() * 0.8), 340, 600);
		int listHeight = clamp((int) (window.getGuiScaledHeight() * 0.5), 100, 300);

		var b = KubeUIScreenBuilder.builder("Quest Editor")
			.draggable()
			.elementSize(totalWidth, 20);

		KubeUIQuestTheme.apply(b);

		b.label("hint", "Quests created here are saved for real and survive a server restart.");

		if (savedQuests.isEmpty()) {
			b.label("empty", "No editor-made quests yet.");
		} else {
			b.scrollPanel(listHeight, panel -> {
				for (var quest : savedQuests) {
					String id = KubeUINbtCompat.getStringOr(quest, "id", "");
					String title = KubeUINbtCompat.getStringOr(quest, "title", id);
					panel.row(row -> {
						row.label("title_" + id, title).width(totalWidth - 170);
						row.button("Edit", ctx -> startEditing(quest)).width(70);
						row.button("Delete", ctx -> {
							var data = new CompoundTag();
							data.putString("questId", id);
							KubeUINetworking.sendAction(KubeUIActions.QUEST_EDITOR_DELETE_ACTION, data);
						}).width(70);
					});
				}
			});
		}

		b.divider();
		b.row(row -> {
			row.button("New Quest", ctx -> startNew()).width(totalWidth / 2 - 5);
			row.button("Close", ctx -> ctx.close()).width(totalWidth / 2 - 5);
		});
		return b;
	}

	private static void startNew() {
		draftId = "";
		draftTitle = "";
		draftDescription = "";
		draftRequires = "";
		draftObjectives.clear();
		draftRewards.clear();
		composing = true;
		refresh();
	}

	private static void startEditing(CompoundTag quest) {
		var def = KubeUIQuests.questFromTag(quest);
		draftId = def.id();
		draftTitle = def.title();
		draftDescription = def.description();
		draftRequires = String.join(",", def.requires());
		draftObjectives.clear();
		draftObjectives.addAll(def.objectives());
		draftRewards.clear();
		draftRewards.addAll(def.rewards());
		composing = true;
		refresh();
	}

	// ---------------------------------------------------------------- compose mode

	/// Everything except the Save/Back footer lives inside a single `.scrollPanel(...)` sized to a
	/// real fraction of *this specific player's* current window (`window.getGuiScaledHeight()`,
	/// re-read every time the screen (re)builds - not a fixed pixel guess) - this form has ~15+
	/// fields once the objective/reward sub-lists are showing, easily taller than a real 900p-ish
	/// window at a normal GUI scale, and previously none of it scrolled: on a small enough
	/// window/GUI-scale combination the "Save Quest" button itself could end up pushed off the
	/// visible screen entirely - unreachable, not just visually cramped, which looked exactly like
	/// "clicking Save does nothing" even though nothing was actually wrong with `#save()` itself.
	/// The objective/reward sub-lists ([#appendObjectiveSection]/[#appendRewardSection]) used to be
	/// their own nested `.scrollPanel`s - removed in favor of plain rows now that the whole form is
	/// one scroll area, since a scroll panel nested inside another isn't a combination anything
	/// else in this codebase exercises, and there's no need to risk it when flattening is simpler
	/// and just as functional.
	private static KubeUIScreenBuilder buildComposeScreen() {
		var window = Minecraft.getInstance().getWindow();
		int totalWidth = clamp((int) (window.getGuiScaledWidth() * 0.85), 380, 660);
		int formHeight = clamp((int) (window.getGuiScaledHeight() * 0.7), 220, 520);

		var b = KubeUIScreenBuilder.builder("Compose Quest")
			.draggable()
			.elementSize(totalWidth, 20);

		b.scrollPanel(formHeight, panel -> {
			panel.textField("id", draftId, "Quest id (e.g. gather_wood)", (ctx, value) -> draftId = value);
			panel.textField("title", draftTitle, "Title", (ctx, value) -> draftTitle = value);
			panel.textArea("description", draftDescription, "Description", 40, (ctx, value) -> draftDescription = value);
			panel.textField("requires", draftRequires, "Requires (comma-separated quest ids, optional)", (ctx, value) -> draftRequires = value);
			panel.divider();

			appendObjectiveSection(panel, totalWidth);
			panel.divider();
			appendRewardSection(panel, totalWidth);
		});

		b.divider();
		b.row(row -> {
			row.button("Save Quest", ctx -> save()).width(totalWidth / 2 - 5);
			row.button("Back", ctx -> {
				composing = false;
				refresh();
			}).width(totalWidth / 2 - 5);
		});
		return b;
	}

	private static void appendObjectiveSection(KubeUIScreenBuilder b, int totalWidth) {
		b.label("objectivesHeading", draftObjectives.isEmpty() ? "No objectives yet." : draftObjectives.size() + " objective(s):");
		for (int i = 0; i < draftObjectives.size(); i++) {
			int index = i;
			var objective = draftObjectives.get(i);
			b.row(row -> {
				row.label("objSummary_" + index, "[" + objective.type() + "] " + objective.label() + " (" + objective.target() + ")").width(totalWidth - 90);
				row.button("Remove", ctx -> {
					draftObjectives.remove(index);
					refresh();
				}).width(70);
			});
		}

		b.row(row -> row.dropdown("objectiveType", OBJECTIVE_TYPES, pendingObjectiveType, (ctx, value) -> {
			pendingObjectiveType = value;
			refresh();
		}).width(140));

		switch (pendingObjectiveType) {
			case "collect" -> b.textField("objItem", pendingObjectiveItemOrEntity, "Item id (e.g. minecraft:oak_log)", (ctx, value) -> pendingObjectiveItemOrEntity = value);
			case "kill" -> b.textField("objEntity", pendingObjectiveItemOrEntity, "Entity id (e.g. minecraft:zombie)", (ctx, value) -> pendingObjectiveItemOrEntity = value);
			case "visit" -> {
				b.textField("objStructureTag", pendingObjectiveStructureTag, "Structure tag (e.g. minecraft:village) - or leave blank and use position below", (ctx, value) -> pendingObjectiveStructureTag = value);
				b.row(row -> {
					row.textField("objDimension", pendingObjectiveDimension, "Dimension", (ctx, value) -> pendingObjectiveDimension = value).width(totalWidth / 4 - 5);
					row.textField("objX", pendingObjectiveX, "X", (ctx, value) -> pendingObjectiveX = value).width(totalWidth / 4 - 5);
					row.textField("objY", pendingObjectiveY, "Y", (ctx, value) -> pendingObjectiveY = value).width(totalWidth / 4 - 5);
					row.textField("objZ", pendingObjectiveZ, "Z", (ctx, value) -> pendingObjectiveZ = value).width(totalWidth / 4 - 5);
				});
				b.textField("objRadius", pendingObjectiveRadius, "Radius", (ctx, value) -> pendingObjectiveRadius = value);
			}
			case "xpLevel" -> b.label("objXpHint", "No extra fields needed - target below is the XP level to reach.");
			default -> b.textField("objCustomId", pendingObjectiveId, "Objective id (used as the counter key)", (ctx, value) -> pendingObjectiveId = value);
		}

		b.row(row -> {
			row.textField("objLabel", pendingObjectiveLabel, "Display label (optional)", (ctx, value) -> pendingObjectiveLabel = value).width(totalWidth / 2 - 45);
			row.textField("objTarget", pendingObjectiveTarget, "Target count", (ctx, value) -> pendingObjectiveTarget = value).width(totalWidth / 2 - 45);
			row.button("Add", ctx -> addObjective()).width(80);
		});
	}

	private static void addObjective() {
		int target = Math.max(1, parseInt(pendingObjectiveTarget, 1));
		var data = new CompoundTag();
		String label = pendingObjectiveLabel.isBlank() ? autoLabel(pendingObjectiveType) : pendingObjectiveLabel;
		String id;

		switch (pendingObjectiveType) {
			case "collect" -> {
				data.putString("item", pendingObjectiveItemOrEntity);
				id = "collect_" + draftObjectives.size();
				if (pendingObjectiveLabel.isBlank()) {
					label = shortId(pendingObjectiveItemOrEntity);
				}
			}
			case "kill" -> {
				data.putString("entity", pendingObjectiveItemOrEntity);
				id = "kill_" + draftObjectives.size();
				if (pendingObjectiveLabel.isBlank()) {
					label = "Kill " + shortId(pendingObjectiveItemOrEntity);
				}
			}
			case "visit" -> {
				if (!pendingObjectiveStructureTag.isBlank()) {
					data.putString("structureTag", pendingObjectiveStructureTag);
				} else {
					data.putString("dimension", pendingObjectiveDimension.isBlank() ? "minecraft:overworld" : pendingObjectiveDimension);
					data.putDouble("x", parseDouble(pendingObjectiveX, 0));
					data.putDouble("y", parseDouble(pendingObjectiveY, 0));
					data.putDouble("z", parseDouble(pendingObjectiveZ, 0));
					data.putDouble("radius", parseDouble(pendingObjectiveRadius, 5));
				}
				id = "visit_" + draftObjectives.size();
				target = 1;
			}
			case "xpLevel" -> id = "xp_" + draftObjectives.size();
			default -> id = pendingObjectiveId.isBlank() ? "custom_" + draftObjectives.size() : pendingObjectiveId;
		}

		draftObjectives.add(new KubeUIQuestObjective(id, pendingObjectiveType, label, target, data));

		pendingObjectiveId = "";
		pendingObjectiveLabel = "";
		pendingObjectiveTarget = "1";
		pendingObjectiveItemOrEntity = "";
		pendingObjectiveStructureTag = "";
		refresh();
	}

	private static String autoLabel(String type) {
		return switch (type) {
			case "xpLevel" -> "Reach XP level";
			case "visit" -> "Visit location";
			default -> "Objective";
		};
	}

	private static void appendRewardSection(KubeUIScreenBuilder b, int totalWidth) {
		b.label("rewardsHeading", draftRewards.isEmpty() ? "No rewards yet." : draftRewards.size() + " reward(s):");
		for (int i = 0; i < draftRewards.size(); i++) {
			int index = i;
			var reward = draftRewards.get(i);
			b.row(row -> {
				row.label("rewardSummary_" + index, "[" + reward.type() + "] " + summarizeReward(reward)).width(totalWidth - 90);
				row.button("Remove", ctx -> {
					draftRewards.remove(index);
					refresh();
				}).width(70);
			});
		}

		b.row(row -> row.dropdown("rewardType", REWARD_TYPES, pendingRewardType, (ctx, value) -> {
			pendingRewardType = value;
			refresh();
		}).width(140));

		switch (pendingRewardType) {
			case "item" -> b.row(row -> {
				row.textField("rewardItem", pendingRewardItem, "Item id", (ctx, value) -> pendingRewardItem = value).width(totalWidth / 2 - 45);
				row.textField("rewardCount", pendingRewardCount, "Count", (ctx, value) -> pendingRewardCount = value).width(totalWidth / 2 - 45);
				row.button("Add", ctx -> addReward()).width(80);
			});
			case "xp" -> b.row(row -> {
				row.textField("rewardLevels", pendingRewardLevels, "XP levels", (ctx, value) -> pendingRewardLevels = value).width(totalWidth - 90);
				row.button("Add", ctx -> addReward()).width(80);
			});
			case "command" -> b.row(row -> {
				row.textField("rewardCommand", pendingRewardCommand, "Command (@s = the player)", (ctx, value) -> pendingRewardCommand = value).width(totalWidth - 90);
				row.button("Add", ctx -> addReward()).width(80);
			});
			default -> {
			}
		}
	}

	private static void addReward() {
		var data = new CompoundTag();
		switch (pendingRewardType) {
			case "item" -> {
				data.putString("item", pendingRewardItem);
				data.putInt("count", Math.max(1, parseInt(pendingRewardCount, 1)));
			}
			case "xp" -> data.putInt("levels", Math.max(0, parseInt(pendingRewardLevels, 1)));
			case "command" -> data.putString("command", pendingRewardCommand);
			default -> {
			}
		}
		draftRewards.add(new KubeUIQuestReward(pendingRewardType, data));
		pendingRewardItem = "";
		pendingRewardCount = "1";
		pendingRewardLevels = "1";
		pendingRewardCommand = "";
		refresh();
	}

	private static String summarizeReward(KubeUIQuestReward reward) {
		return switch (reward.type()) {
			case "item" -> KubeUINbtCompat.getIntOr(reward.data(), "count", 1) + "x " + shortId(KubeUINbtCompat.getStringOr(reward.data(), "item", ""));
			case "xp" -> KubeUINbtCompat.getIntOr(reward.data(), "levels", 0) + " level(s)";
			case "command" -> KubeUINbtCompat.getStringOr(reward.data(), "command", "");
			default -> "";
		};
	}

	/// Previously, a missing id or an empty objective list made this silently `return` - no chat
	/// message, no toast, no visual change at all, which is exactly what "I click Save and nothing
	/// happens" looks like from the player's side even though it was working as coded. Both cases
	/// now say why via [KubeUIToast] (the same non-modal notification `KubeUI.toast(...)` uses),
	/// and a successful save also flips back to the list view immediately instead of waiting on the
	/// server's reply to arrive first - [#receiveList] still refreshes again a moment later with
	/// the authoritative list, this is just an instant "yes, this happened" instead of a
	/// network-latency-dependent one.
	private static void save() {
		if (draftId.isBlank()) {
			KubeUIToast.add("Quest id is required.", 3000);
			return;
		}
		if (draftObjectives.isEmpty()) {
			KubeUIToast.add("Add at least one objective before saving.", 3000);
			return;
		}

		var requires = new ArrayList<String>();
		for (var part : draftRequires.split(",")) {
			if (!part.isBlank()) {
				requires.add(part.trim());
			}
		}

		var def = new KubeUIQuestDef(
			draftId.trim(),
			draftTitle.isBlank() ? draftId.trim() : draftTitle,
			draftDescription,
			requires,
			List.copyOf(draftObjectives),
			List.copyOf(draftRewards),
			"editor"
		);

		KubeUINetworking.sendAction(KubeUIActions.QUEST_EDITOR_SAVE_ACTION, KubeUIQuests.questToTag(def));
		KubeUIToast.add("Quest '" + def.title() + "' saved.", 2500);
		composing = false;
		refresh();
	}

	private static String shortId(String id) {
		int colon = id.indexOf(':');
		return colon >= 0 ? id.substring(colon + 1) : id;
	}

	private static int parseInt(String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	private static double parseDouble(String value, double fallback) {
		try {
			return Double.parseDouble(value.trim());
		} catch (Exception ex) {
			return fallback;
		}
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
