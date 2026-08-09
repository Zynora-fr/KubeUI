package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/// Client-side half of `KubeUI.dialogue(dialogueId, npcUuid)` and every dialogue choice -
/// sends the request, then builds/rebuilds a "visual novel" screen from each
/// [KubeUIDialogue] reply: a portrait ([#buildScreen]'s `entityPreview`/`item`), the
/// node's text revealed a few characters per tick rather than all at once (driven by
/// [KubeUIScreenBuilder#onTick], not a separate `Screen#tick()` override), an optional countdown
/// that auto-picks a `timedOut` choice once it hits zero, and a button per
/// server-approved choice. `text`/each choice's `label` may arrive as a plain string (`text`/`label`
/// with an empty `textKey`/`labelKey`) or a translation key to resolve - both resolved
/// via `Language.getInstance().getOrDefault(...)`, the exact same call `.labelKey(...)` itself
/// already makes, not a second mechanism.
final class KubeUIDialogueBridge {
	/// Characters revealed per client tick (20/second) - ~6/tick is roughly 120 characters/second,
	/// fast enough not to feel like waiting on a long line, slow enough to actually read as a
	/// reveal rather than an instant flash.
	private static final int CHARS_PER_TICK = 6;

	private static String currentDialogueId = "";
	private static String currentNodeId = "";
	private static String currentNpcUuid = "";
	private static String fullText = "";
	private static int revealedChars;
	private static Long timerDeadlineMillis;

	private KubeUIDialogueBridge() {
	}

	static void open(String dialogueId, String npcUuid) {
		var data = new CompoundTag();
		data.putString("dialogueId", dialogueId);
		data.putString("npcUuid", npcUuid == null ? "" : npcUuid);
		KubeUINetworking.sendAction(KubeUIActions.DIALOGUE_OPEN_ACTION, data);
	}

	static void receive(CompoundTag data) {
		if (KubeUINbtCompat.getBooleanOr(data, "ended", false)) {
			KubeUIScreenBuilder.close();
			return;
		}
		buildScreen(data);
	}

	private static void buildScreen(CompoundTag data) {
		currentDialogueId = KubeUINbtCompat.getStringOr(data, "dialogueId", "");
		currentNodeId = KubeUINbtCompat.getStringOr(data, "nodeId", "");
		currentNpcUuid = KubeUINbtCompat.getStringOr(data, "npcUuid", "");
		fullText = resolveText(KubeUINbtCompat.getStringOr(data, "textKey", ""), KubeUINbtCompat.getStringOr(data, "text", ""));

		int timerSeconds = KubeUINbtCompat.getIntOr(data, "timerSeconds", 0);
		revealedChars = 0;
		timerDeadlineMillis = timerSeconds > 0 ? System.currentTimeMillis() + timerSeconds * 1000L : null;

		playSound(KubeUINbtCompat.getStringOr(data, "sound", ""));

		var builder = KubeUIScreenBuilder.builder("Dialogue").draggable().elementSize(300, 20);

		addPortrait(builder, KubeUINbtCompat.getStringOr(data, "portraitType", "none"), KubeUINbtCompat.getStringOr(data, "portrait", ""));

		builder.label("dialogueText", "");
		if (timerSeconds > 0) {
			builder.label("dialogueTimer", String.valueOf(timerSeconds));
		}
		builder.divider();

		for (var choiceEntry : KubeUINbtCompat.getListOrEmpty(data, "choices")) {
			if (!(choiceEntry instanceof CompoundTag choiceTag)) {
				continue;
			}
			int index = KubeUINbtCompat.getIntOr(choiceTag, "index", -1);
			String label = resolveText(KubeUINbtCompat.getStringOr(choiceTag, "labelKey", ""), KubeUINbtCompat.getStringOr(choiceTag, "label", ""));
			builder.button(label, ctx -> sendChoice(index, false));
		}

		builder.onTick(KubeUIDialogueBridge::tick);
		builder.open();
	}

	private static void addPortrait(KubeUIScreenBuilder builder, String portraitType, String portraitId) {
		if ("entity".equals(portraitType)) {
			var entityType = KubeUIQuests.resolveEntityType(portraitId);
			if (entityType != null) {
				builder.entityPreview(entityType);
			}
		} else if ("item".equals(portraitType)) {
			var item = BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(portraitId)).orElse(null);
			if (item != null) {
				builder.item(item, 1);
			}
		}
	}

	private static String resolveText(String key, String fallback) {
		return key.isEmpty() ? fallback : Language.getInstance().getOrDefault(key, fallback);
	}

	private static void tick(KubeUIContext ctx) {
		if (revealedChars < fullText.length()) {
			revealedChars = Math.min(fullText.length(), revealedChars + CHARS_PER_TICK);
			ctx.setLabel("dialogueText", fullText.substring(0, revealedChars));
		}

		if (timerDeadlineMillis != null) {
			long remainingMs = timerDeadlineMillis - System.currentTimeMillis();
			int remainingSeconds = (int) Math.max(0, Math.ceil(remainingMs / 1000.0));
			ctx.setLabel("dialogueTimer", String.valueOf(remainingSeconds));
			if (remainingMs <= 0) {
				timerDeadlineMillis = null;
				sendChoice(-1, true);
			}
		}
	}

	private static void sendChoice(int index, boolean timedOut) {
		var data = new CompoundTag();
		data.putString("dialogueId", currentDialogueId);
		data.putString("nodeId", currentNodeId);
		data.putInt("choiceIndex", index);
		data.putBoolean("timedOut", timedOut);
		data.putString("npcUuid", currentNpcUuid);
		KubeUINetworking.sendAction(KubeUIActions.DIALOGUE_CHOOSE_ACTION, data);
	}

	private static void playSound(String soundId) {
		if (soundId == null || soundId.isBlank()) {
			return;
		}
		var id = ResourceLocation.tryParse(soundId);
		if (id == null) {
			return;
		}
		var sound = SoundEvent.createVariableRangeEvent(id);
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
	}
}
