package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.function.Consumer;

/// Passed to every KubeUI callback (button click, toggle/slider/dropdown/etc. change) so scripts
/// can react by reading or updating other widgets on the same screen, or closing it, without
/// rebuilding it from scratch.
public class KubeUIContext {
	private final KubeUIScreen screen;

	KubeUIContext(KubeUIScreen screen) {
		this.screen = screen;
	}

	/// Changes the text of a `label(id, ...)` widget. No-op if `id` doesn't exist.
	public void setLabel(String id, String text) {
		var widget = screen.labels.get(id);
		if (widget != null) {
			widget.setMessage(Component.literal(text));
		}
	}

	/// Reads the current value of a `textField(id, ...)` widget, or null if `id` doesn't exist.
	public String getTextFieldValue(String id) {
		var widget = screen.textFields.get(id);
		return widget != null ? widget.getValue() : null;
	}

	/// Overwrites the current value of a `textField(id, ...)` widget. No-op if `id` doesn't exist.
	public void setTextFieldValue(String id, String value) {
		var widget = screen.textFields.get(id);
		if (widget != null) {
			widget.setValue(value);
		}
	}

	/// Reads the current value of a `textArea(id, ...)` widget, or null if `id` doesn't exist.
	public String getTextAreaValue(String id) {
		var widget = screen.textAreas.get(id);
		return widget != null ? widget.getValue() : null;
	}

	/// Overwrites the current value of a `textArea(id, ...)` widget. No-op if `id` doesn't exist.
	public void setTextAreaValue(String id, String value) {
		var widget = screen.textAreas.get(id);
		if (widget != null) {
			widget.setValue(value);
		}
	}

	/// Reads the current value of a `slider(id, ...)` widget (0 if `id` doesn't exist).
	public double getSliderValue(String id) {
		var widget = screen.sliders.get(id);
		return widget != null ? widget.currentValue() : 0;
	}

	/// Moves a `slider(id, ...)` widget to `value` without firing its `onChange`. No-op if `id`
	/// doesn't exist.
	public void setSliderValue(String id, double value) {
		var widget = screen.sliders.get(id);
		if (widget != null) {
			widget.setCurrentValue(value);
		}
	}

	/// Reads the current value of a `toggle(id, ...)` checkbox (false if `id` doesn't exist).
	public boolean getToggleValue(String id) {
		var widget = screen.toggles.get(id);
		return widget != null && widget.selected();
	}

	/// Sets a `toggle(id, ...)` checkbox to `value`. Unlike the other `setXxxValue` methods, this
	/// *does* fire its `onChange` (like a real click would) - `Checkbox` doesn't expose a silent
	/// setter. No-op (and no `onChange` call) if `id` doesn't exist or is already `value`.
	public void setToggleValue(String id, boolean value) {
		var widget = screen.toggles.get(id);
		if (widget != null && widget.selected() != value) {
			widget.onPress(null);
		}
	}

	/// Reads the current value of a `dropdown(id, ...)` widget, or null if `id` doesn't exist.
	public String getDropdownValue(String id) {
		var widget = screen.dropdowns.get(id);
		return widget != null ? widget.getValue() : null;
	}

	/// Sets a `dropdown(id, ...)` widget to `value` (must be one of its declared options).
	/// No-op if `id` doesn't exist.
	public void setDropdownValue(String id, String value) {
		var widget = screen.dropdowns.get(id);
		if (widget != null) {
			widget.setValue(value);
		}
	}

	/// Reads the currently selected option of a `radioGroup(id, ...)`, or null if `id` doesn't exist.
	public String getRadioValue(String id) {
		var handle = screen.radioGroups.get(id);
		return handle != null ? handle.selected : null;
	}

	/// Selects `value` in a `radioGroup(id, ...)` (must be one of its declared options), updating
	/// every option's label. No-op if `id` doesn't exist or `value` isn't a valid option.
	public void setRadioValue(String id, String value) {
		var handle = screen.radioGroups.get(id);
		if (handle == null || !handle.options.contains(value)) {
			return;
		}

		handle.selected = value;
		for (int i = 0; i < handle.buttons.size(); i++) {
			String option = handle.options.get(i);
			handle.buttons.get(i).setMessage(Component.literal((option.equals(value) ? "> " : "  ") + option));
		}
	}

	/// Reads the current value of a `number(id, ...)` spinner (0 if `id` doesn't exist).
	public int getNumberValue(String id) {
		var handle = screen.numbers.get(id);
		return handle != null ? handle.value : 0;
	}

	/// Sets a `number(id, ...)` spinner to `value`, clamped to its declared [min, max]. No-op if
	/// `id` doesn't exist.
	public void setNumberValue(String id, int value) {
		var handle = screen.numbers.get(id);
		if (handle != null) {
			handle.value = Math.max(handle.min, Math.min(handle.max, value));
			handle.field.setValue(String.valueOf(handle.value));
		}
	}

	/// Reads the current value (0 if unset) of a `rating(id, ...)` widget.
	public int getRatingValue(String id) {
		var widget = screen.ratings.get(id);
		return widget != null ? widget.value() : 0;
	}

	/// Sets a `rating(id, ...)` widget's value directly (doesn't fire `onChange` - same
	/// silent-setter convention as every other `setXxxValue`). No-op if `id` doesn't exist.
	public void setRatingValue(String id, int value) {
		var widget = screen.ratings.get(id);
		if (widget != null) {
			widget.setValue(value);
		}
	}

	/// Reads the current raw key code (0 if unset) bound to a `keybindCapture(id, ...)` widget.
	public int getKeybindValue(String id) {
		var widget = screen.keybindCaptures.get(id);
		return widget != null ? widget.keyCode() : 0;
	}

	/// Reads the currently-selected indices (into the `items` list it was built from, ascending)
	/// of a `selectableList(id, ...)` - empty if nothing's selected or `id` doesn't exist.
	public List<Integer> getSelectedListItems(String id) {
		var state = screen.listSelections.get(id);
		if (state == null) {
			return List.of();
		}
		var sorted = new java.util.ArrayList<>(state.selected);
		java.util.Collections.sort(sorted);
		return sorted;
	}

	/// Reads the current color of a `colorPicker(id, ...)` as an opaque ARGB int (0 if `id`
	/// doesn't exist).
	public int getColor(String id) {
		var handle = screen.colorPickers.get(id);
		return handle != null ? handle.color : 0;
	}

	/// Sets a `colorPicker(id, ...)` to `color`, an opaque ARGB value (`long` for the same reason
	/// as [KubeUIScreenBuilder#colorPicker]). No-op if `id` doesn't exist.
	public void setColor(String id, long color) {
		var handle = screen.colorPickers.get(id);
		if (handle != null) {
			handle.color = (int) color;
			handle.hexField.setValue(KubeUILayoutMath.formatHexColor(handle.color));
		}
	}

	/// Reads the currently checked options of a `checkboxGroup(id, ...)`, or an empty list if
	/// `id` doesn't exist.
	public List<String> getCheckboxGroupValues(String id) {
		var handle = screen.checkboxGroups.get(id);
		return handle != null ? KubeUIScreen.selectedOf(handle) : List.of();
	}

	/// Reads the current value of a `progressBar(id, ...)` (0 if `id` doesn't exist).
	public double getProgress(String id) {
		var widget = screen.progressBars.get(id);
		return widget != null ? widget.value() : 0;
	}

	/// Updates a `progressBar(id, ...)`'s value (and optionally its max). No-op if `id` doesn't
	/// exist.
	public void setProgress(String id, double value) {
		var widget = screen.progressBars.get(id);
		if (widget != null) {
			widget.set(value, widget.maxValue());
		}
	}

	/// Updates a `progressBar(id, ...)`'s value and max. No-op if `id` doesn't exist.
	public void setProgress(String id, double value, double max) {
		var widget = screen.progressBars.get(id);
		if (widget != null) {
			widget.set(value, max);
		}
	}

	/// Enables/disables (greys out) the element with this id - its own declared id, or one set via
	/// `.id(...)`. For a composite element (radioGroup, checkboxGroup, number, colorPicker), this
	/// affects every widget inside it. No-op if `id` doesn't exist.
	public void setEnabled(String id, boolean enabled) {
		var element = screen.byId.get(id);
		if (element != null) {
			element.visitWidgets(w -> w.active = enabled);
		}
	}

	/// Shows/hides the element with this id - its own declared id, or one set via `.id(...)`. For
	/// a composite element, this affects every widget inside it. Hidden elements keep their
	/// reserved layout space (they don't collapse). No-op if `id` doesn't exist.
	public void setVisible(String id, boolean visible) {
		var element = screen.byId.get(id);
		if (element != null) {
			element.visitWidgets(w -> w.visible = visible);
		}
	}

	/// Mutates the screen's underlying builder (add elements, `.remove(id)` existing ones, ...)
	/// and immediately rebuilds the screen to reflect the change. Existing widget state (text
	/// field contents, slider positions, ...) for elements that survive the update is *not*
	/// preserved - they're recreated fresh from their original spec.
	public void update(Consumer<KubeUIScreenBuilder> mutator) {
		mutator.accept(screen.builder);
		screen.rebuild();
	}

	/// Gives the local player `count` of `item`, via the normal client -> server `/give` command
	/// pipeline (same as typing it in chat) - so it's subject to the same permission checks as any
	/// command (the player needs command access: cheats enabled in singleplayer, or operator in
	/// multiplayer; otherwise this silently does nothing, same as typing the command manually
	/// would). This is a convenience for simple cases (see `kubeui_test_shop.js`) - a shop that
	/// must be trusted in normal survival should validate the purchase server-side instead
	/// (see `KubeUIActions`), not rely on the client only calling this when it's supposed to.
	public void giveItem(Item item, int count) {
		var player = Minecraft.getInstance().player;
		if (player == null || count <= 0) {
			return;
		}
		var id = BuiltInRegistries.ITEM.getKey(item);
		player.connection.sendCommand("give @s " + id + " " + count);
	}

	/// Sends `action` (with `data`, which may be an empty/null NBT compound) to the server, to be
	/// handled by whatever `server_scripts` registered via `KubeUIActions.register(action, ...)`.
	/// Unlike [#giveItem], the server fully controls what this does and whether it's allowed -
	/// use this (not `giveItem`) for anything that needs to be trustworthy in normal survival.
	/// No-op (does nothing, not even a warning) if `action` isn't registered server-side.
	public void runServerAction(String action, CompoundTag data) {
		KubeUINetworking.sendAction(action, data);
	}

	/// Closes the screen - immediately, or after a fade-out if the screen used `.animated()`.
	public void close() {
		screen.requestClose();
	}

	/// Replaces this screen with `newBuilder`'s, cross-fading between the two over `durationMs`
	/// instead of the abrupt close-then-open a plain `.close()` + `newBuilder.open()` would be -
	/// this screen keeps rendering (fading out) while the new one fades in over it.
	public void transitionTo(KubeUIScreenBuilder newBuilder, int durationMs) {
		var next = new KubeUIScreen(newBuilder);
		next.beginCrossFadeFrom(screen, durationMs);
		Minecraft.getInstance().setScreen(next);
	}

	/// Same as [#transitionTo(KubeUIScreenBuilder, int)], with a 200ms cross-fade.
	public void transitionTo(KubeUIScreenBuilder newBuilder) {
		transitionTo(newBuilder, 200);
	}

	/// Briefly shakes the screen and flashes it red - for signaling a refused/invalid action (a
	/// disabled button clicked, an empty required field) without interrupting the player with a
	/// blocking `KubeUI.alert(...)`.
	public void shake() {
		screen.shake();
	}
}
