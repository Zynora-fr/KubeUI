package dev.kubeui.gui;

import net.minecraft.resources.Identifier;

/// Shared visual identity for the quest screens ([KubeUIQuestLogScreen]/[KubeUIQuestGiverScreen]/
/// [KubeUIQuestEditorScreen]) - a custom-drawn, nine-slice panel texture
/// (`assets/kubeui/textures/gui/quest_panel.png`: a dark charcoal body with a soft glowing teal
/// accent border and rounded corners, built for the fixed nine-slice convention - see
/// [KubeUIPanelBackground]'s own class doc for exactly what shape that expects).
///
/// Applied via [KubeUIScreenBuilder#windowBackground] - a real panel drawn *behind* the whole
/// screen's content by [KubeUIScreen] itself, not a normal-flow decorative element sitting
/// somewhere in the row/column stack (see `.windowBackground(...)`'s own doc for why a widget-tree
/// element can't achieve that safely in this layout system - `.absolute(...)` always paints on
/// top, never behind, and normal-flow elements never overlap by construction).
final class KubeUIQuestTheme {
	static final Identifier PANEL_TEXTURE = Identifier.fromNamespaceAndPath("kubeui", "textures/gui/quest_panel.png");

	private KubeUIQuestTheme() {
	}

	static void apply(KubeUIScreenBuilder b) {
		b.windowBackground(PANEL_TEXTURE);
	}
}
