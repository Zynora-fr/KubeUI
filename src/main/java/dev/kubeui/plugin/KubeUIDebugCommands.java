package dev.kubeui.plugin;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.kubeui.KubeUI;
import dev.kubeui.gui.KubeUIConfig;
import dev.kubeui.gui.KubeUIDebug;
import dev.kubeui.gui.KubeUIFileEditor;
import dev.kubeui.gui.KubeUIPreferences;
import dev.kubeui.gui.KubeUIQuestEditorScreen;
import dev.kubeui.gui.KubeUIQuestLogScreen;
import dev.kubeui.gui.KubeUIRecipeDesignerScreen;
import dev.kubeui.gui.KubeUIScreenBuilder;
import dev.kubeui.gui.KubeUITraderDesignerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/// `/kubeui debug` (report on the most recently opened KubeUI screen - a chat command can't
/// inspect the *currently* open one, since typing it closes that screen first),
/// `/kubeui outline` (toggle widget bounding-box outlines), `/kubeui grid` (toggle a layout debug
/// grid with pixel dimensions, on top of the outline), `/kubeui screenshot`,
/// `/kubeui scale [factor]` (shrink/grow every KubeUI screen - see [KubeUIScreenBuilder#setScale]),
/// `/kubeui fontscale [factor]` (text-only equivalent - see [KubeUIScreenBuilder#setFontScale]),
/// `/kubeui theme preview <name>` (try a named theme preset for a few seconds - see
/// [KubeUIScreenBuilder#previewTheme]), `/kubeui export`/`/kubeui import` (portable preferences
/// snapshot - see [KubeUIPreferences]), `/kubeui reload-config` (report the config values
/// currently in effect - see [KubeUIConfig]), `/kubeui editor` (open the in-game
/// `kubejs/client_scripts` file manager/editor - see [KubeUIFileEditor]), `/kubeui profile`
/// (timing of the last screen build), `/kubeui stresstest` (build/open many synthetic screens
/// back to back and report timing - see [KubeUIDebug]), `/kubeui recipe-designer` (define
/// custom crafting/furnace/blast-furnace/smithing recipes in-game - see
/// [KubeUIRecipeDesignerScreen]), `/kubeui trader-designer` (build a custom trader and get a
/// real spawn-egg-style item for it - see [KubeUITraderDesignerScreen]), `/kubeui quest-log`
/// (view active/available/completed quests and progress - see [KubeUIQuestLogScreen] - also
/// reachable as the shorter top-level `/quest`, which also has `/quest accept <questId>`/
/// `/quest complete <questId>` for starting/turning in a quest with no NPC/block giver needed),
/// and `/kubeui quest-editor` (compose a quest without writing a script - see
/// [KubeUIQuestEditorScreen]). See [KubeUIServerCommands] for the real server-side `/money`
/// command (`balance`/`pay` self-service, `deposit`/`withdraw` OP-only).
@EventBusSubscriber(modid = KubeUI.MOD_ID, value = Dist.CLIENT)
final class KubeUIDebugCommands {
	private KubeUIDebugCommands() {
	}

	@SubscribeEvent
	static void register(RegisterClientCommandsEvent event) {
		event.getDispatcher().register(
			Commands.literal("kubeui")
				.then(Commands.literal("debug").executes(ctx -> debug(ctx.getSource())))
				.then(Commands.literal("outline").executes(ctx -> outline(ctx.getSource())))
				.then(Commands.literal("grid").executes(ctx -> grid(ctx.getSource())))
				.then(Commands.literal("screenshot").executes(ctx -> screenshot(ctx.getSource())))
				.then(Commands.literal("scale")
					.executes(ctx -> scaleStatus(ctx.getSource()))
					.then(Commands.argument("factor", FloatArgumentType.floatArg(0.5f, 2.0f))
						.executes(ctx -> scaleSet(ctx.getSource(), FloatArgumentType.getFloat(ctx, "factor")))))
				.then(Commands.literal("fontscale")
					.executes(ctx -> fontScaleStatus(ctx.getSource()))
					.then(Commands.argument("factor", FloatArgumentType.floatArg(0.5f, 2.0f))
						.executes(ctx -> fontScaleSet(ctx.getSource(), FloatArgumentType.getFloat(ctx, "factor")))))
				.then(Commands.literal("theme")
					.then(Commands.literal("preview")
						.then(Commands.argument("name", StringArgumentType.word())
							.executes(ctx -> themePreview(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))
				.then(Commands.literal("export").executes(ctx -> export(ctx.getSource())))
				.then(Commands.literal("import").executes(ctx -> importPreferences(ctx.getSource())))
				.then(Commands.literal("reload-config").executes(ctx -> reloadConfig(ctx.getSource())))
				.then(Commands.literal("editor").executes(ctx -> editor(ctx.getSource())))
				.then(Commands.literal("profile").executes(ctx -> profile(ctx.getSource())))
				.then(Commands.literal("stresstest").executes(ctx -> stressTest(ctx.getSource())))
				.then(Commands.literal("recipe-designer").executes(ctx -> recipeDesigner(ctx.getSource())))
				.then(Commands.literal("trader-designer").executes(ctx -> traderDesigner(ctx.getSource())))
				.then(Commands.literal("quest-log").executes(ctx -> questLog(ctx.getSource())))
				.then(Commands.literal("quest-editor").executes(ctx -> questEditor(ctx.getSource())))
		);

		// A short top-level alias for the quest-system commands a regular player (not someone
		// building content) actually needs day to day - "/kubeui quest-log" is the real name for
		// the log and still works, this is just an easier one to remember/type. `accept`/`complete`
		// let a player start/turn in a quest purely by id, with no NPC/block giver required - see
		// [KubeUIQuestLogScreen#acceptQuest]/[#completeQuest].
		event.getDispatcher().register(
			Commands.literal("quest")
				.executes(ctx -> questLog(ctx.getSource()))
				.then(Commands.literal("accept")
					.then(Commands.argument("questId", StringArgumentType.string())
						.executes(ctx -> questAccept(ctx.getSource(), StringArgumentType.getString(ctx, "questId")))))
				.then(Commands.literal("complete")
					.then(Commands.argument("questId", StringArgumentType.string())
						.executes(ctx -> questComplete(ctx.getSource(), StringArgumentType.getString(ctx, "questId")))))
		);
	}

	private static int questAccept(CommandSourceStack source, String questId) {
		KubeUIQuestLogScreen.acceptQuest(questId);
		return 1;
	}

	private static int questComplete(CommandSourceStack source, String questId) {
		KubeUIQuestLogScreen.completeQuest(questId);
		return 1;
	}

	private static int questLog(CommandSourceStack source) {
		KubeUIQuestLogScreen.open();
		return 1;
	}

	private static int questEditor(CommandSourceStack source) {
		KubeUIQuestEditorScreen.open();
		return 1;
	}

	private static int recipeDesigner(CommandSourceStack source) {
		KubeUIRecipeDesignerScreen.open();
		return 1;
	}

	private static int traderDesigner(CommandSourceStack source) {
		KubeUITraderDesignerScreen.open();
		return 1;
	}

	private static int profile(CommandSourceStack source) {
		var summary = KubeUIDebug.lastProfileSummary();
		if (summary == null) {
			source.sendSystemMessage(Component.literal("No KubeUI screen has built anything yet this session."));
			return 0;
		}
		source.sendSystemMessage(Component.literal(summary));
		return 1;
	}

	private static int stressTest(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal(KubeUIDebug.runStressTest()));
		return 1;
	}

	private static int editor(CommandSourceStack source) {
		KubeUIFileEditor.open();
		return 1;
	}

	private static int debug(CommandSourceStack source) {
		var summary = KubeUIDebug.lastOpenedSummary();

		if (summary == null) {
			source.sendSystemMessage(Component.literal("No KubeUI screen has been opened yet this session."));
			return 0;
		}

		source.sendSystemMessage(Component.literal("Last KubeUI screen:\n" + summary));
		return 1;
	}

	private static int outline(CommandSourceStack source) {
		boolean enabled = !KubeUIDebug.isOutlineEnabled();
		KubeUIDebug.setOutlineEnabled(enabled);
		source.sendSystemMessage(Component.literal("KubeUI widget outlines: " + (enabled ? "ON" : "OFF")));
		return 1;
	}

	private static int grid(CommandSourceStack source) {
		boolean enabled = !KubeUIDebug.isGridEnabled();
		KubeUIDebug.setGridEnabled(enabled);
		source.sendSystemMessage(Component.literal("KubeUI layout debug grid: " + (enabled ? "ON" : "OFF")));
		return 1;
	}

	private static int screenshot(CommandSourceStack source) {
		var mc = Minecraft.getInstance();
		Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(), component -> mc.execute(() -> source.sendSystemMessage(component)));
		return 1;
	}

	private static int scaleStatus(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal(
			"KubeUI scale: " + KubeUIScreenBuilder.getScale() + " (0.5-2.0). Set with /kubeui scale <factor>."
		));
		return 1;
	}

	private static int scaleSet(CommandSourceStack source, float factor) {
		KubeUIScreenBuilder.setScale(factor);
		source.sendSystemMessage(Component.literal(
			"KubeUI scale set to " + KubeUIScreenBuilder.getScale() + " - already-open screens need to be closed and reopened to pick it up."
		));
		return 1;
	}

	private static int fontScaleStatus(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal(
			"KubeUI font scale: " + KubeUIScreenBuilder.getFontScale() + " (0.5-2.0). Set with /kubeui fontscale <factor>."
		));
		return 1;
	}

	private static int fontScaleSet(CommandSourceStack source, float factor) {
		KubeUIScreenBuilder.setFontScale(factor);
		source.sendSystemMessage(Component.literal(
			"KubeUI font scale set to " + KubeUIScreenBuilder.getFontScale() + " - already-open screens need to be closed and reopened to pick it up."
		));
		return 1;
	}

	private static final int THEME_PREVIEW_MS = 5000;

	private static int themePreview(CommandSourceStack source, String name) {
		boolean known = KubeUIScreenBuilder.previewTheme(name, THEME_PREVIEW_MS);
		if (!known) {
			source.sendSystemMessage(Component.literal(
				"Unknown theme preset '" + name + "' - try default, dark, light, high-contrast, or one a script registered."
			));
			return 0;
		}

		source.sendSystemMessage(Component.literal(
			"Previewing theme '" + name + "' for " + (THEME_PREVIEW_MS / 1000) + "s - reverts automatically."
		));
		return 1;
	}

	private static int export(CommandSourceStack source) {
		try {
			KubeUIPreferences.export();
			source.sendSystemMessage(Component.literal("KubeUI preferences exported to " + KubeUIPreferences.exportFile()));
			return 1;
		} catch (Exception ex) {
			KubeUI.LOGGER.error("KubeUI: /kubeui export failed", ex);
			source.sendSystemMessage(Component.literal("Export failed - see the log for details."));
			return 0;
		}
	}

	private static int importPreferences(CommandSourceStack source) {
		try {
			boolean found = KubeUIPreferences.importPreferences();
			if (!found) {
				source.sendSystemMessage(Component.literal("No export found at " + KubeUIPreferences.exportFile() + " - run /kubeui export on the other install first."));
				return 0;
			}
			source.sendSystemMessage(Component.literal("KubeUI preferences imported - already-open screens need to be closed and reopened to pick them up."));
			return 1;
		} catch (Exception ex) {
			KubeUI.LOGGER.error("KubeUI: /kubeui import failed", ex);
			source.sendSystemMessage(Component.literal("Import failed - see the log for details."));
			return 0;
		}
	}

	/// NeoForge already watches `config/kubeui-common.toml` on disk and reloads it automatically
	/// the moment it changes (`net.neoforged.fml.config.ConfigWatcher`, verified against the real
	/// decompiled class - not something KubeUI implements itself) - this command doesn't need to
	/// *trigger* a reload, only report the values currently in effect, so a player who just
	/// hand-edited the file can confirm it actually took without waiting or guessing.
	private static int reloadConfig(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal(
			"KubeUI config (auto-reloads on file change - this just reports current values):\n"
				+ "  defaultScale = " + KubeUIConfig.defaultScale.get() + "\n"
				+ "  defaultFontScale = " + KubeUIConfig.defaultFontScale.get() + "\n"
				+ "  defaultTheme = " + KubeUIConfig.defaultTheme.get() + "\n"
				+ "  persistScaleAndTheme = " + KubeUIConfig.persistScaleAndTheme.get()
		));
		return 1;
	}
}
