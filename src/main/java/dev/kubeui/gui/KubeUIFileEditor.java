package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import dev.latvian.mods.kubejs.KubeJSPaths;
import dev.latvian.mods.kubejs.client.KubeJSClient;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/// A real in-game file manager + text editor for `kubejs/client_scripts`
/// ([KubeJSPaths#CLIENT_SCRIPTS]) - list, create, open, edit and delete `.js` files without
/// leaving the game, typing real code straight into a [KubeUIScreenBuilder#textArea] instead of
/// composing a layout by clicking widgets together (the original, scrapped visual-editor
/// approach) or being limited to one fixed canvas file (the second, also superseded approach).
/// "Save & Reload" writes the file then calls the same, real
/// `KubeJSClient#reloadClientScripts` behind `/kubejs reload client-scripts`, so a change is
/// visible immediately. `/kubeui editor` ([dev.kubeui.plugin.KubeUIDebugCommands]) just opens this
/// screen - it holds all its state as static fields, the same pattern the rest of KubeUI's
/// singleton in-game tools (`KubeUIDebug`, etc.) already use.
public final class KubeUIFileEditor {
	private static String selectedFile;
	private static String pendingContent = "";
	private static String newFileName = "";
	private static String statusMessage = "";

	private KubeUIFileEditor() {
	}

	public static void open() {
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	private static void refresh() {
		Minecraft.getInstance().setScreen(new KubeUIScreen(buildScreen()));
	}

	/// A file browser column (left) next to a code editor column (right), like a real IDE, instead
	/// of one long stacked list - every dimension below is computed fresh from the *actual current*
	/// window (`Window#getGuiScaledWidth/Height`, the same logical-pixel space Minecraft's own GUI
	/// Scale option controls) each time this rebuilds, so it's generously sized on a large window
	/// and still fully usable - never clipped - on a small one, the same adaptive feel vanilla
	/// Minecraft screens have. `.scrollPanel(...)`'s width/height aren't affected by a chained
	/// `.width(...)`/`.height(...)` the way most elements are (they take their size from the nested
	/// content builder's own `elementWidth`/the panel's own height argument instead) - so each
	/// column's nested builder gets its own `.elementSize(...)` rather than relying on that.
	private static KubeUIScreenBuilder buildScreen() {
		var window = Minecraft.getInstance().getWindow();
		int screenW = window.getGuiScaledWidth();
		int screenH = window.getGuiScaledHeight();

		int totalWidth = clamp((int) (screenW * 0.82), 300, 760);
		int contentHeight = clamp((int) (screenH * 0.6), 140, 340);
		int leftWidth = clamp((int) (totalWidth * 0.32), 120, 220);
		int rightWidth = Math.max(140, totalWidth - leftWidth - COLUMN_GAP);

		var b = KubeUIScreenBuilder.builder("KubeUI Script Editor")
			.draggable()
			// Deliberately not .resizable(...): that would fix this screen's height at whatever
			// number is passed to it regardless of the player's actual window - .resizable(...)
			// opts out of KubeUIScreen's own automatic safety net that instead auto-wraps content
			// in a scroll area sized to whatever's *actually* available (the "never truly cut off"
			// fallback in rebuild()), on top of the sizing already computed above.
			.elementSize(totalWidth, 20)
			.windowBackground(KubeUIPanelTextures.FILE_EDITOR);

		b.row(row -> {
			row.textField("newFileName", newFileName, "new_file.js", (ctx, value) -> newFileName = value)
				.width(Math.max(120, totalWidth - 90));
			row.button("New file", ctx -> createFile()).width(70);
		});
		b.divider();

		var files = listFiles();
		b.row(row -> {
			row.scrollPanel(contentHeight, filePanel -> {
				filePanel.elementSize(leftWidth, 18);
				filePanel.label("filesLabel", files.isEmpty() ? "No files yet" : files.size() + " file(s):");
				for (String file : files) {
					boolean isSelected = file.equals(selectedFile);
					filePanel.row(fileRow -> {
						fileRow.button((isSelected ? "> " : "") + file, ctx -> selectFile(file)).width(leftWidth - 26);
						fileRow.button("x", ctx -> confirmDelete(file)).width(20);
					});
				}
			});

			row.scrollPanel(contentHeight, editorPanel -> {
				editorPanel.elementSize(rightWidth, 20);
				if (selectedFile != null) {
					editorPanel.label("editingLabel", "Editing: " + selectedFile);
					editorPanel.textArea("content", pendingContent, "", Math.max(60, contentHeight - 50), (ctx, value) -> pendingContent = value);
					editorPanel.row(actions -> {
						actions.button("Save", ctx -> saveFile(false)).width(70);
						actions.button("Save & Reload", ctx -> saveFile(true)).width(95);
					});
				} else {
					editorPanel.label("pickHint1", "Open a file on the left,");
					editorPanel.label("pickHint2", "or create a new one above.");
				}
			});
		});

		if (!statusMessage.isEmpty()) {
			b.divider();
			b.label("status", statusMessage);
		}

		b.divider();
		b.button("Close", ctx -> ctx.close()).width(90);
		return b;
	}

	private static final int COLUMN_GAP = 8;

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private static List<String> listFiles() {
		if (Files.notExists(KubeJSPaths.CLIENT_SCRIPTS)) {
			return List.of();
		}
		try (var walk = Files.walk(KubeJSPaths.CLIENT_SCRIPTS)) {
			return walk
				.filter(Files::isRegularFile)
				.filter(p -> p.toString().endsWith(".js"))
				.map(p -> KubeJSPaths.CLIENT_SCRIPTS.relativize(p).toString().replace('\\', '/'))
				.sorted()
				.toList();
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to list {}", KubeJSPaths.CLIENT_SCRIPTS, ex);
			return List.of();
		}
	}

	/// Resolves `relativePath` against `client_scripts` and rejects it (rather than silently
	/// clamping it) if `..` segments would walk it outside that directory - a typo in the "new
	/// file" field shouldn't be able to touch anything else on disk.
	private static Path resolveSafe(String relativePath) throws IOException {
		var root = KubeJSPaths.CLIENT_SCRIPTS.normalize();
		var resolved = root.resolve(relativePath).normalize();
		if (!resolved.startsWith(root)) {
			throw new IOException("Refusing to access a path outside kubejs/client_scripts: " + relativePath);
		}
		return resolved;
	}

	private static void createFile() {
		String name = newFileName.trim();
		if (name.isEmpty()) {
			return;
		}
		if (!name.endsWith(".js")) {
			name = name + ".js";
		}
		try {
			var path = resolveSafe(name);
			if (Files.notExists(path)) {
				Files.createDirectories(path.getParent());
				Files.writeString(path, "// " + name + "\n", StandardCharsets.UTF_8);
			}
			newFileName = "";
			selectFile(KubeJSPaths.CLIENT_SCRIPTS.relativize(path).toString().replace('\\', '/'));
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to create {}", name, ex);
			statusMessage = "Failed to create " + name + " - see the log.";
			refresh();
		}
	}

	private static void selectFile(String relativePath) {
		try {
			var path = resolveSafe(relativePath);
			pendingContent = Files.readString(path, StandardCharsets.UTF_8);
			selectedFile = relativePath;
			statusMessage = "";
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to read {}", relativePath, ex);
			statusMessage = "Failed to read " + relativePath + " - see the log.";
		}
		refresh();
	}

	private static void saveFile(boolean reload) {
		if (selectedFile == null) {
			return;
		}
		try {
			var path = resolveSafe(selectedFile);
			Files.writeString(path, pendingContent, StandardCharsets.UTF_8);
			statusMessage = "Saved " + selectedFile + (reload ? " - scripts reloaded." : ".");
			if (reload) {
				KubeJSClient.reloadClientScripts();
			}
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to save {}", selectedFile, ex);
			statusMessage = "Failed to save " + selectedFile + " - see the log.";
		}
		refresh();
	}

	private static void confirmDelete(String relativePath) {
		KubeUIScreenBuilder.confirm(
			"Delete file?",
			"Delete " + relativePath + "?\nThis can't be undone.",
			() -> deleteFile(relativePath),
			() -> {
			}
		);
	}

	private static void deleteFile(String relativePath) {
		try {
			Files.deleteIfExists(resolveSafe(relativePath));
			if (relativePath.equals(selectedFile)) {
				selectedFile = null;
				pendingContent = "";
			}
			statusMessage = "Deleted " + relativePath + ".";
		} catch (IOException ex) {
			KubeUI.LOGGER.error("KubeUI: failed to delete {}", relativePath, ex);
			statusMessage = "Failed to delete " + relativePath + " - see the log.";
		}
		refresh();
	}
}
