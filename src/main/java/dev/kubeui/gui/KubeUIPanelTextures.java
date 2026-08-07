package dev.kubeui.gui;

import net.minecraft.resources.Identifier;

/// Real, custom-drawn nine-slice panel textures for the mod's other built-in screens (see
/// [KubeUIQuestTheme] for the quest system's own, parchment-styled one) - same dark charcoal
/// panel body every one shares (so they read as one consistent family), each with its own accent
/// border color so a player can tell at a glance which system they're in: amber for the recipe
/// designer, gold for the trader designer, cyan for the file editor, violet for the recipe
/// browser (`KubeUI.recipeScreen(...)`/`.recipesFor(...)`). Applied the same way as the quest
/// screens - [KubeUIScreenBuilder#windowBackground], a real panel [KubeUIScreen] draws behind the
/// whole screen, not a normal-flow widget.
final class KubeUIPanelTextures {
	static final Identifier RECIPE_DESIGNER = Identifier.fromNamespaceAndPath("kubeui", "textures/gui/recipe_panel.png");
	static final Identifier TRADER_DESIGNER = Identifier.fromNamespaceAndPath("kubeui", "textures/gui/trader_panel.png");
	static final Identifier FILE_EDITOR = Identifier.fromNamespaceAndPath("kubeui", "textures/gui/editor_panel.png");
	static final Identifier RECIPE_BROWSER = Identifier.fromNamespaceAndPath("kubeui", "textures/gui/browser_panel.png");

	private KubeUIPanelTextures() {
	}
}
