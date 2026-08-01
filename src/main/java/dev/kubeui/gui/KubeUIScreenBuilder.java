package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/// Script-facing entry point for building a widget screen. Exposed to KubeJS scripts as the
/// global `KubeUI` binding (client scripts only).
///
/// By default, elements stack vertically, centered on screen. `.row(...)`, `.grid(...)` and
/// `.scrollPanel(...)` take a callback that receives a *nested* builder - the same class, reused
/// recursively - to lay out a group of elements horizontally, in a grid, or in a scrollable area.
///
/// Example (KubeJS client script):
/// ```js
/// KubeUI.builder('My Menu')
///     .label('status', 'Pick something')
///     .row(row => row
///         .button('Yes', screen => screen.setLabel('status', 'Yes!'))
///         .button('No', screen => screen.setLabel('status', 'No!')))
///     .button('Close', screen => screen.close())
///     .open()
/// ```
public class KubeUIScreenBuilder {
	/// Keyed by `persistKey` (see [#builder(String, String)]), captured when a screen with that
	/// key closes and restored as the initial values when a screen with the same key opens again.
	static final Map<String, Map<String, Object>> PERSISTED = new HashMap<>();

	final List<Entry> entries = new ArrayList<>();
	final List<Tab> tabs = new ArrayList<>();
	final List<Binding> bindings = new ArrayList<>();
	Component title = Component.empty();
	int elementWidth = 200;
	int buttonHeight = 20;
	String persistKey;

	float anchorX = 0.5f;
	float anchorY = 0.5f;
	int anchorMarginX = 0;
	int anchorMarginY = 0;

	boolean draggable = false;
	Integer resizableMinHeight;
	Integer resizableMaxHeight;

	boolean animated = false;
	int animationDurationMs = 200;

	Identifier customFont;

	Consumer<KubeUIContext> onOpenCallback;
	Consumer<KubeUIContext> onCloseCallback;

	private KubeUIScreenBuilder() {
	}

	public static KubeUIScreenBuilder builder(String title) {
		return builder(title, null);
	}

	/// `persistKey` (if non-null) makes text fields/sliders/toggles/etc. remember their values
	/// across separate `.open()` calls that share the same key, overriding their declared initial
	/// values with whatever they were last set to. Use a stable, unique key per distinct screen
	/// (e.g. `"myaddon:settings"`) - screens with no key (or `.builder(title)`) don't persist.
	public static KubeUIScreenBuilder builder(String title, String persistKey) {
		var b = new KubeUIScreenBuilder();
		b.title = Component.literal(title);
		b.persistKey = persistKey;
		return b;
	}

	/// Adds a clickable button. `onClick` receives the [KubeUIContext] for the open screen.
	public KubeUIScreenBuilder button(String text, Consumer<KubeUIContext> onClick) {
		return add(new ButtonElement(text, safe(onClick)));
	}

	/// Adds a plain text line. Its text can be changed later via [KubeUIContext#setLabel].
	public KubeUIScreenBuilder label(String id, String text) {
		return add(new LabelElement(id, text));
	}

	/// Adds a plain text line resolved from the active language file's `langKey`, falling back to
	/// `fallbackText` if that key doesn't exist (e.g. no translation was ever added for it).
	/// Resolved once, when the screen is built - a mid-game language change needs the screen
	/// rebuilt (`screen.update(b -> {})`, or just reopened) to pick up the new text.
	public KubeUIScreenBuilder labelKey(String id, String langKey, String fallbackText) {
		return add(new LabelElement(id, net.minecraft.locale.Language.getInstance().getOrDefault(langKey, fallbackText)));
	}

	/// Adds a checkbox. `onChange` fires every time it's toggled, with the new value.
	public KubeUIScreenBuilder toggle(String id, String text, boolean initial, BiConsumer<KubeUIContext, Boolean> onChange) {
		return add(new ToggleElement(id, text, initial, safeBi(onChange)));
	}

	/// Adds a single-line text field. `hint` (may be null/empty) shows as greyed-out placeholder
	/// text. `onChange` fires on every keystroke with the field's current value.
	public KubeUIScreenBuilder textField(String id, String initialValue, String hint, BiConsumer<KubeUIContext, String> onChange) {
		return add(new TextFieldElement(id, initialValue, hint, safeBi(onChange)));
	}

	/// Adds a slider whose value is remapped from [min, max]. `onChange` fires while dragging.
	public KubeUIScreenBuilder slider(String id, double min, double max, double initial, BiConsumer<KubeUIContext, Double> onChange) {
		return add(new SliderElement(id, min, max, initial, safeBi(onChange)));
	}

	/// Adds a click-to-cycle dropdown ("select") over a fixed list of string options.
	public KubeUIScreenBuilder dropdown(String id, List<String> options, String initial, BiConsumer<KubeUIContext, String> onChange) {
		if (!requireNonEmptyOptions("dropdown", id, options)) {
			return this;
		}
		return add(new DropdownElement(id, options, initial, safeBi(onChange)));
	}

	/// Adds an exclusive group of options (only one selected at a time), stacked vertically.
	public KubeUIScreenBuilder radioGroup(String id, List<String> options, String initial, BiConsumer<KubeUIContext, String> onChange) {
		if (!requireNonEmptyOptions("radioGroup", id, options)) {
			return this;
		}
		return add(new RadioGroupElement(id, options, initial, safeBi(onChange)));
	}

	/// Adds a multi-line text box, `height` pixels tall. `onChange` fires on every keystroke.
	public KubeUIScreenBuilder textArea(String id, String initialValue, String hint, int height, BiConsumer<KubeUIContext, String> onChange) {
		return add(new TextAreaElement(id, initialValue, hint, height, safeBi(onChange)));
	}

	/// Adds a static image, scaled to fill `width` x `height`. Not updatable afterwards.
	public KubeUIScreenBuilder image(Identifier texture, int width, int height) {
		return add(new ImageElement(texture, width, height));
	}

	/// Adds a single item icon (with its count badge), rendered like an inventory slot.
	public KubeUIScreenBuilder item(Item item, int count) {
		return item(item, count, null);
	}

	/// Same as [#item(Item, int)], but clickable - `onClick` (may be null) receives the
	/// [KubeUIContext] for the open screen.
	public KubeUIScreenBuilder item(Item item, int count, Consumer<KubeUIContext> onClick) {
		return add(new ItemElement(new ItemStack(item, count), safe(onClick)));
	}

	/// Adds a widget registered by a third-party **Java** mod via [KubeUIWidgets#register].
	/// `args` are passed through as-is to that mod's [KubeUIWidgetFactory]. If `type` isn't
	/// registered, shows a visible "Unknown custom widget" placeholder instead of failing.
	public KubeUIScreenBuilder custom(String type, Object... args) {
		return add(new CustomElement(type, args));
	}

	/// Adds a read-only progress bar showing `value / max`. Update it via [KubeUIContext#setProgress].
	public KubeUIScreenBuilder progressBar(String id, double value, double max) {
		return add(new ProgressBarElement(id, value, max));
	}

	/// Adds a thin horizontal separator line.
	public KubeUIScreenBuilder divider() {
		return add(new DividerElement());
	}

	/// Adds empty space, `height` pixels tall, to fine-tune layout manually.
	public KubeUIScreenBuilder spacer(int height) {
		return add(new SpacerElement(height));
	}

	/// Adds an integer spinner (`-` / value / `+`), clamped to [min, max].
	public KubeUIScreenBuilder number(String id, int min, int max, int initial, BiConsumer<KubeUIContext, Integer> onChange) {
		return add(new NumberElement(id, min, max, initial, safeBi(onChange)));
	}

	/// Adds a basic color picker: a row of preset swatches plus a `#RRGGBB` hex field.
	/// `initial` is an opaque ARGB value (`0xFFrrggbb`) - `long` because that literal is bigger
	/// than `Integer.MAX_VALUE` and Rhino won't silently wrap a JS number into a Java `int`.
	/// The value passed to `onChange` is the same ARGB packing, narrowed to `int`.
	public KubeUIScreenBuilder colorPicker(String id, long initial, BiConsumer<KubeUIContext, Integer> onChange) {
		return add(new ColorPickerElement(id, (int) initial, safeBi(onChange)));
	}

	/// Adds an independent multi-select group of checkboxes, stacked vertically. `onChange`
	/// fires on every toggle with the full list of currently checked options.
	public KubeUIScreenBuilder checkboxGroup(String id, List<String> options, List<String> initialSelected, BiConsumer<KubeUIContext, List<String>> onChange) {
		return add(new CheckboxGroupElement(id, options, initialSelected, safeBi(onChange)));
	}

	/// Lays out a group of elements horizontally instead of the default vertical stack.
	/// `children` receives a nested builder to populate with the row's content.
	public KubeUIScreenBuilder row(Consumer<KubeUIScreenBuilder> children) {
		var nested = childBuilder();
		children.accept(nested);
		return add(new RowElement(nested));
	}

	/// Lays out a group of elements in a grid with `columns` columns, wrapping automatically.
	/// `children` receives a nested builder to populate with the grid's content.
	public KubeUIScreenBuilder grid(int columns, Consumer<KubeUIScreenBuilder> children) {
		var nested = childBuilder();
		children.accept(nested);
		return add(new GridElement(columns, nested));
	}

	/// Wraps a vertically-stacked group of elements in a scrollable viewport, `maxHeight` pixels
	/// tall. `children` receives a nested builder to populate with the panel's content.
	public KubeUIScreenBuilder scrollPanel(int maxHeight, Consumer<KubeUIScreenBuilder> children) {
		var nested = childBuilder();
		children.accept(nested);
		return add(new ScrollPanelElement(maxHeight, nested));
	}

	/// Adds one row per entry in `items`, calling `renderer.render(row, item, index)` to build
	/// each one. `items` may hold any script values (strings, numbers, objects).
	public KubeUIScreenBuilder list(String id, List<?> items, KubeUIListItemRenderer renderer) {
		for (int i = 0; i < items.size(); i++) {
			Object item = items.get(i);
			int index = i;
			row(row -> {
				try {
					renderer.render(row, item, index);
				} catch (Exception e) {
					KubeUI.LOGGER.error("KubeUI: error rendering list('{}', ...) item at index {}", id, index, e);
				}
			});
		}
		return this;
	}

	/// Adds a tab. `children` receives a nested builder to populate with that tab's content.
	/// A tab bar is shown above the content of whichever tab is currently active (the first one,
	/// initially). Using `.tab(...)` replaces this builder's own content entirely - don't mix
	/// `.tab(...)` calls with `.button(...)`/etc. on the same (root) builder.
	public KubeUIScreenBuilder tab(String name, Consumer<KubeUIScreenBuilder> children) {
		var nested = childBuilder();
		children.accept(nested);
		tabs.add(new Tab(name, nested));
		return this;
	}

	/// Makes the screen's content draggable by its title. Root builder only.
	public KubeUIScreenBuilder draggable() {
		this.draggable = true;
		return this;
	}

	/// Adds a drag handle to the content's bottom-right corner that resizes its visible height
	/// (dragging vertically) between `minHeight` and `maxHeight`, scrolling if the content is
	/// taller than the current height. Root builder only.
	public KubeUIScreenBuilder resizable(int minHeight, int maxHeight) {
		this.resizableMinHeight = minHeight;
		this.resizableMaxHeight = maxHeight;
		return this;
	}

	/// Removes the element with this id (its own declared id, or one set via `.id(...)`) if one
	/// was added earlier. No-op if none match. Meant for use from [KubeUIContext#update].
	public KubeUIScreenBuilder remove(String id) {
		entries.removeIf(e -> id.equals(e.effectiveId()));
		return this;
	}

	/// Fades the screen's widgets in on open and out on close (200ms). Root builder only.
	public KubeUIScreenBuilder animated() {
		return animated(200);
	}

	/// Same as [#animated()], with an explicit fade duration in milliseconds.
	public KubeUIScreenBuilder animated(int durationMs) {
		this.animated = true;
		this.animationDurationMs = Math.max(1, durationMs);
		return this;
	}

	/// Renders every label/button in this screen with a resource-pack-provided font instead of
	/// the default. Root builder only.
	public KubeUIScreenBuilder font(Identifier fontId) {
		this.customFont = fontId;
		return this;
	}

	/// Called once, right after the screen finishes opening.
	public KubeUIScreenBuilder onOpen(Consumer<KubeUIContext> callback) {
		this.onOpenCallback = safe(callback);
		return this;
	}

	/// Called once, right before the screen actually closes (for any reason: `screen.close()`,
	/// Esc, or switching to another screen).
	public KubeUIScreenBuilder onClose(Consumer<KubeUIContext> callback) {
		this.onCloseCallback = safe(callback);
		return this;
	}

	/// Polls `valueSupplier` every client tick; whenever the value it returns changes (by
	/// `equals`), calls `onChange` with the new value. Use this to drive a widget from external,
	/// changing state (e.g. `screen.setLabel`/`setProgress` from a value read off a player/block)
	/// without writing your own tick handler.
	public KubeUIScreenBuilder bind(Supplier<Object> valueSupplier, BiConsumer<KubeUIContext, Object> onChange) {
		bindings.add(new Binding(safeSupplier(valueSupplier), safeBi(onChange)));
		return this;
	}

	/// Sets the width (and, for buttons/sliders/dropdowns, height) elements are laid out at.
	/// Applies to elements added after this call.
	public KubeUIScreenBuilder elementSize(int width, int buttonHeight) {
		this.elementWidth = width;
		this.buttonHeight = buttonHeight;
		return this;
	}

	/// Overrides the width of the last-added element (instead of the builder's default `elementWidth`).
	public KubeUIScreenBuilder width(int width) {
		lastStyle().width = width;
		lastStyle().widthPercent = null;
		return this;
	}

	/// Overrides the height of the last-added element (instead of its type's default height).
	public KubeUIScreenBuilder height(int height) {
		lastStyle().height = height;
		lastStyle().heightPercent = null;
		return this;
	}

	/// Same as [#width(int)], but as a percentage of the whole screen's current width (e.g.
	/// `"50%"`) instead of a fixed pixel count - resolved fresh every time the screen builds or
	/// resizes, so it stays proportional on any window size/resolution instead of overflowing or
	/// looking tiny depending on the player's screen. Ignored (falls back to the pixel default) if
	/// `spec` isn't a valid `"<number>%"` string.
	public KubeUIScreenBuilder width(String spec) {
		Float pct = KubeUILayoutMath.parsePercent(spec);
		if (pct != null) {
			lastStyle().widthPercent = pct;
			lastStyle().width = null;
		} else {
			KubeUI.LOGGER.error("Invalid width '{}' - expected a pixel count or a percentage like \"50%\"", spec);
		}
		return this;
	}

	/// Same as [#height(int)], but as a percentage of the whole screen's current height - see
	/// [#width(String)].
	public KubeUIScreenBuilder height(String spec) {
		Float pct = KubeUILayoutMath.parsePercent(spec);
		if (pct != null) {
			lastStyle().heightPercent = pct;
			lastStyle().height = null;
		} else {
			KubeUI.LOGGER.error("Invalid height '{}' - expected a pixel count or a percentage like \"50%\"", spec);
		}
		return this;
	}

	/// Sets the horizontal alignment (`"left"`, `"center"` or `"right"`) of the last-added element
	/// within the space allotted to it (relevant when it's narrower than its column/cell).
	public KubeUIScreenBuilder align(String horizontal) {
		lastStyle().alignX = switch (horizontal) {
			case "left" -> 0.0f;
			case "right" -> 1.0f;
			default -> 0.5f;
		};
		return this;
	}

	/// Sets the padding (in pixels) around the last-added element, inside the space allotted to it.
	public KubeUIScreenBuilder padding(int padding) {
		return padding(padding, padding, padding, padding);
	}

	/// Sets horizontal/vertical padding (in pixels) around the last-added element.
	public KubeUIScreenBuilder padding(int horizontal, int vertical) {
		return padding(horizontal, vertical, horizontal, vertical);
	}

	/// Sets per-side padding (in pixels) around the last-added element.
	public KubeUIScreenBuilder padding(int left, int top, int right, int bottom) {
		var style = lastStyle();
		style.paddingLeft = left;
		style.paddingTop = top;
		style.paddingRight = right;
		style.paddingBottom = bottom;
		return this;
	}

	/// Shows `text` in a tooltip when hovering the last-added element.
	public KubeUIScreenBuilder tooltip(String text) {
		lastStyle().tooltip = text;
		return this;
	}

	/// Sets an explicit Tab-key focus order group for the last-added element (lower first).
	/// Widgets without an explicit order keep their natural (layout) order.
	public KubeUIScreenBuilder tabOrder(int order) {
		lastStyle().tabOrder = order;
		return this;
	}

	/// Overrides the screen-reader narration text for the last-added element. Only takes effect
	/// on elements that don't already narrate something meaningful on their own (divider, spacer,
	/// progressBar, image, item) - interactive vanilla widgets (button, toggle, textField, ...)
	/// already narrate their own label/value and ignore this.
	public KubeUIScreenBuilder narration(String text) {
		lastStyle().narration = text;
		return this;
	}

	/// Plays `soundId` (in addition to the default click sound) when the last-added element is
	/// interacted with. Supported on `button`, `toggle` and `item`.
	public KubeUIScreenBuilder sound(Identifier soundId) {
		lastStyle().clickSound = soundId;
		return this;
	}

	/// Assigns an explicit id to the last-added element, for [KubeUIContext#setEnabled],
	/// [KubeUIContext#setVisible] or [#remove]. Elements that already take an id (label,
	/// textField, slider, ...) are reachable by that id automatically - this is for element types
	/// that don't otherwise have one (button, image, item, divider, spacer).
	public KubeUIScreenBuilder id(String id) {
		lastStyle().id = id;
		return this;
	}

	/// Positions the screen's content instead of centering it. `preset` is one of `"center"`
	/// (the default), `"top"`, `"bottom"`, `"left"`, `"right"`, `"top-left"`, `"top-right"`,
	/// `"bottom-left"`, `"bottom-right"`. Only meaningful on the root builder (`KubeUI.builder(...)`).
	public KubeUIScreenBuilder anchor(String preset) {
		return anchor(preset, 0, 0);
	}

	/// Same as [#anchor(String)], additionally inset from the relevant screen edges by
	/// `marginX`/`marginY` pixels.
	public KubeUIScreenBuilder anchor(String preset, int marginX, int marginY) {
		switch (preset) {
			case "top-left" -> setAnchor(0f, 0f);
			case "top" -> setAnchor(0.5f, 0f);
			case "top-right" -> setAnchor(1f, 0f);
			case "left" -> setAnchor(0f, 0.5f);
			case "right" -> setAnchor(1f, 0.5f);
			case "bottom-left" -> setAnchor(0f, 1f);
			case "bottom" -> setAnchor(0.5f, 1f);
			case "bottom-right" -> setAnchor(1f, 1f);
			default -> setAnchor(0.5f, 0.5f);
		}
		anchorMarginX = marginX;
		anchorMarginY = marginY;
		return this;
	}

	private void setAnchor(float x, float y) {
		anchorX = x;
		anchorY = y;
	}

	public void open() {
		Minecraft.getInstance().setScreen(new KubeUIScreen(this));
	}

	public static void close() {
		Minecraft.getInstance().setScreen(null);
	}

	/// Sets the global color theme applied to every screen built from now on. Colors are opaque
	/// ARGB values (`0xFFrrggbb`) - `long` for the same reason as [#colorPicker]. Only affects
	/// what KubeUI draws directly (title, progress bar fill, divider) - see [KubeUITheme].
	public static void setTheme(long titleColor, long accentColor, long textColor) {
		KubeUITheme.set((int) titleColor, (int) accentColor, (int) textColor);
	}

	/// Restores the default theme.
	public static void resetTheme() {
		KubeUITheme.reset();
	}

	/// Multiplies the pixel-based width/height of every widget on every screen built from now on
	/// (clamped to 0.5-2.0) - a global "make it smaller/bigger" knob, independent of Minecraft's
	/// own GUI Scale option, for when a script's UI doesn't fit comfortably on a given player's
	/// screen. Doesn't affect `.width("50%")`-style percentages, which are already relative to the
	/// screen. Players can also reach this themselves via `/kubeui scale <factor>` without needing
	/// script support.
	public static void setScale(double scale) {
		KubeUITheme.setScale((float) scale);
	}

	/// Restores the default (1.0) scale.
	public static void resetScale() {
		KubeUITheme.resetScale();
	}

	/// The current global scale (see [#setScale(double)]) - also used by `/kubeui scale`.
	public static float getScale() {
		return KubeUITheme.uiScale;
	}

	/// Opens a small Yes/No dialog on top of whatever screen is currently open (including another
	/// KubeUI screen), returning to it either way. `message` may contain `\n` for multiple lines.
	public static void confirm(String title, String message, Runnable onYes, Runnable onNo) {
		var mc = Minecraft.getInstance();
		var parent = mc.screen;
		var b = builder(title);
		var safeYes = safeRunnable(onYes);
		var safeNo = safeRunnable(onNo);

		String[] lines = message.split("\n");
		for (int i = 0; i < lines.length; i++) {
			b.label("line" + i, lines[i]);
		}

		b.row(row -> row
			.button("Yes", s -> {
				mc.setScreen(parent);
				if (safeYes != null) {
					safeYes.run();
				}
			}).width(90)
			.button("No", s -> {
				mc.setScreen(parent);
				if (safeNo != null) {
					safeNo.run();
				}
			}).width(90));

		b.open();
	}

	/// Opens a small OK dialog on top of whatever screen is currently open (including another
	/// KubeUI screen), returning to it once dismissed. `message` may contain `\n` for multiple lines.
	public static void alert(String title, String message, Runnable onClose) {
		var mc = Minecraft.getInstance();
		var parent = mc.screen;
		var b = builder(title);
		var safeClose = safeRunnable(onClose);

		String[] lines = message.split("\n");
		for (int i = 0; i < lines.length; i++) {
			b.label("line" + i, lines[i]);
		}

		b.button("OK", s -> {
			mc.setScreen(parent);
			if (safeClose != null) {
				safeClose.run();
			}
		}).width(90);

		b.open();
	}

	private KubeUIScreenBuilder add(Element element) {
		var entry = new Entry(element);
		String id = entry.effectiveId();

		if (id != null && entries.stream().anyMatch(e -> id.equals(e.effectiveId()))) {
			KubeUI.LOGGER.warn("KubeUI: duplicate element id '{}' added to the same builder - lookups by id will only ever reach one of them.", id);
		}

		entries.add(entry);
		return this;
	}

	private boolean requireNonEmptyOptions(String widgetType, String id, List<String> options) {
		if (options == null || options.isEmpty()) {
			KubeUI.LOGGER.error("KubeUI: .{}('{}', ...) needs at least one option - skipping it instead of crashing the whole screen.", widgetType, id);
			return false;
		}
		return true;
	}

	/// A bug in a script's callback (e.g. `values.lenght` instead of `.length`) shouldn't be able
	/// to crash the whole game via an uncaught exception bubbling out of Minecraft's input
	/// handling - every callback taken by this class is wrapped with this (or [#safeBi]) at the
	/// point it's stored, logging instead of propagating.
	private static Consumer<KubeUIContext> safe(Consumer<KubeUIContext> callback) {
		if (callback == null) {
			return null;
		}
		return ctx -> {
			try {
				callback.accept(ctx);
			} catch (Exception e) {
				KubeUI.LOGGER.error("KubeUI: error in a screen callback", e);
			}
		};
	}

	private static <T> BiConsumer<KubeUIContext, T> safeBi(BiConsumer<KubeUIContext, T> callback) {
		if (callback == null) {
			return null;
		}
		return (ctx, value) -> {
			try {
				callback.accept(ctx, value);
			} catch (Exception e) {
				KubeUI.LOGGER.error("KubeUI: error in a widget callback", e);
			}
		};
	}

	private static Runnable safeRunnable(Runnable callback) {
		if (callback == null) {
			return null;
		}
		return () -> {
			try {
				callback.run();
			} catch (Exception e) {
				KubeUI.LOGGER.error("KubeUI: error in a confirm()/alert() callback", e);
			}
		};
	}

	private static Supplier<Object> safeSupplier(Supplier<Object> supplier) {
		return () -> {
			try {
				return supplier.get();
			} catch (Exception e) {
				KubeUI.LOGGER.error("KubeUI: error in a bind() supplier", e);
				return null;
			}
		};
	}

	private Style lastStyle() {
		if (entries.isEmpty()) {
			throw new IllegalStateException("Call width()/height()/align()/padding() after adding an element, not before");
		}
		return entries.get(entries.size() - 1).style;
	}

	private KubeUIScreenBuilder childBuilder() {
		var b = new KubeUIScreenBuilder();
		b.elementWidth = this.elementWidth;
		b.buttonHeight = this.buttonHeight;
		return b;
	}

	/// Per-element layout/behavior overrides, mutated in place by width()/height()/align()/
	/// padding()/tooltip()/tabOrder()/narration()/id() - they always apply to whichever element
	/// was added most recently.
	static final class Style {
		Integer width;
		Integer height;
		Float widthPercent;
		Float heightPercent;
		float alignX = 0.5f;
		float alignY = 0.5f;
		int paddingLeft;
		int paddingTop;
		int paddingRight;
		int paddingBottom;
		String tooltip;
		Integer tabOrder;
		String narration;
		String id;
		Identifier clickSound;
	}

	static final class Binding {
		final Supplier<Object> supplier;
		final BiConsumer<KubeUIContext, Object> onChange;
		Object lastValue = new Object();

		Binding(Supplier<Object> supplier, BiConsumer<KubeUIContext, Object> onChange) {
			this.supplier = supplier;
			this.onChange = onChange;
		}
	}

	static final class Entry {
		final Element element;
		final Style style = new Style();

		Entry(Element element) {
			this.element = element;
		}

		String effectiveId() {
			return style.id != null ? style.id : naturalId(element);
		}
	}

	/// The id an element type declares on its own (label/textField/slider/...), or null for
	/// types that don't have one (button/image/item/divider/spacer/row/grid/scrollPanel) unless
	/// one was assigned via `.id(...)`.
	private static String naturalId(Element element) {
		return switch (element) {
			case LabelElement e -> e.id();
			case ToggleElement e -> e.id();
			case TextFieldElement e -> e.id();
			case SliderElement e -> e.id();
			case DropdownElement e -> e.id();
			case RadioGroupElement e -> e.id();
			case TextAreaElement e -> e.id();
			case ProgressBarElement e -> e.id();
			case NumberElement e -> e.id();
			case ColorPickerElement e -> e.id();
			case CheckboxGroupElement e -> e.id();
			default -> null;
		};
	}

	record Tab(String name, KubeUIScreenBuilder content) {
	}

	sealed interface Element {
	}

	record ButtonElement(String text, Consumer<KubeUIContext> onClick) implements Element {
	}

	record LabelElement(String id, String text) implements Element {
	}

	record ToggleElement(String id, String text, boolean initial, BiConsumer<KubeUIContext, Boolean> onChange) implements Element {
	}

	record TextFieldElement(String id, String initialValue, String hint, BiConsumer<KubeUIContext, String> onChange) implements Element {
	}

	record SliderElement(String id, double min, double max, double initial, BiConsumer<KubeUIContext, Double> onChange) implements Element {
	}

	record DropdownElement(String id, List<String> options, String initial, BiConsumer<KubeUIContext, String> onChange) implements Element {
	}

	record RadioGroupElement(String id, List<String> options, String initial, BiConsumer<KubeUIContext, String> onChange) implements Element {
	}

	record TextAreaElement(String id, String initialValue, String hint, int height, BiConsumer<KubeUIContext, String> onChange) implements Element {
	}

	record ImageElement(Identifier texture, int width, int height) implements Element {
	}

	record ItemElement(ItemStack stack, Consumer<KubeUIContext> onClick) implements Element {
	}

	record CustomElement(String type, Object[] args) implements Element {
	}

	record ProgressBarElement(String id, double value, double max) implements Element {
	}

	record DividerElement() implements Element {
	}

	record SpacerElement(int height) implements Element {
	}

	record NumberElement(String id, int min, int max, int initial, BiConsumer<KubeUIContext, Integer> onChange) implements Element {
	}

	record ColorPickerElement(String id, int initial, BiConsumer<KubeUIContext, Integer> onChange) implements Element {
	}

	record CheckboxGroupElement(String id, List<String> options, List<String> initialSelected, BiConsumer<KubeUIContext, List<String>> onChange) implements Element {
	}

	record RowElement(KubeUIScreenBuilder content) implements Element {
	}

	record GridElement(int columns, KubeUIScreenBuilder content) implements Element {
	}

	record ScrollPanelElement(int maxHeight, KubeUIScreenBuilder content) implements Element {
	}
}
