package dev.kubeui.gui;

import dev.kubeui.KubeUI;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

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
	final Map<Integer, Consumer<KubeUIContext>> hotkeys = new HashMap<>();
	Component title = Component.empty();
	int elementWidth = 200;
	int buttonHeight = 20;
	String persistKey;

	float anchorX = 0.5f;
	float anchorY = 0.5f;
	int anchorMarginX = 0;
	int anchorMarginY = 0;

	boolean draggable = false;
	boolean snapToEdges = false;
	boolean nonModal = false;
	boolean minimizable = false;
	Integer resizableMinHeight;
	Integer resizableMaxHeight;
	float renderScale = 1.0f;

	boolean animated = false;
	int animationDurationMs = 200;
	String animationType = "fade";
	String animationEasing = "linear";

	Identifier customFont;

	String backgroundMode = "dirt";
	Identifier backgroundTexture;

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

	/// Adds a multi-style block of wrapped text, unlike `.label()` which is a single flat style.
	/// Build `text` with vanilla `Component`/`Style` (`.withBold(true)`, `.withColor(...)`,
	/// `.append(...)` for more runs) - not a KubeUI-specific styling API.
	public KubeUIScreenBuilder richText(String id, Component text) {
		return richText(id, text, null);
	}

	/// Same as [#richText(String, Component)], but clickable.
	public KubeUIScreenBuilder richText(String id, Component text, Consumer<KubeUIContext> onClick) {
		return add(new RichTextElement(id, text, safe(onClick)));
	}

	/// Adds a small colored status pill, e.g. "New"/"In stock"/"Sold out". `color` is an opaque
	/// ARGB value (`long` for the same overflow reason as [#colorPicker]). Purely decorative.
	public KubeUIScreenBuilder badge(String text, long color) {
		return add(new BadgeElement(text, (int) color));
	}

	/// Adds a row of stars (1..max). Clickable unless `onChange` is null (read-only display).
	public KubeUIScreenBuilder rating(String id, int max, int initial, BiConsumer<KubeUIContext, Integer> onChange) {
		return add(new RatingElement(id, max, initial, safeBi(onChange)));
	}

	/// Adds an indeterminate loading spinner, for a moment spent waiting on the server (e.g.
	/// right after `screen.runServerAction(...)`, before the response arrives). Static state -
	/// there's nothing script-visible to update on it besides adding/removing it.
	public KubeUIScreenBuilder spinner(String id) {
		return add(new SpinnerElement(id));
	}

	/// Adds a nine-slice background panel (corners fixed-size, edges/center stretched) - unlike
	/// `.image(...)`, which stretches the whole texture uniformly. See [KubeUIPanelBackground]
	/// for the fixed texture-size/border convention this currently assumes.
	public KubeUIScreenBuilder panelBackground(Identifier texture) {
		return add(new PanelBackgroundElement(texture));
	}

	/// Adds a live-rendered preview of an entity type (idle pose, mildly turned), via the same
	/// mechanism vanilla uses for the player model in the survival inventory screen. The entity
	/// is constructed for rendering only - it's never added to a level, so it doesn't tick, take
	/// damage, or become visible to anyone else.
	public KubeUIScreenBuilder entityPreview(EntityType<?> entityType) {
		return add(new EntityPreviewElement(entityType));
	}

	/// A block "preview" rendered as its item icon (see [KubeUIEntityPreview]'s class doc for why
	/// this isn't true 3D block rendering - the picture-in-picture GUI render pipeline this
	/// version exposes only has a render-state type for entities, not blocks).
	public KubeUIScreenBuilder blockPreview(Block block, int count) {
		return item(block.asItem(), count);
	}

	/// Adds a rebindable-key field: click to start listening, the next key press becomes the new
	/// binding. `initial`/the value passed to `onChange` are raw key codes (same type
	/// `KeyEvent#key()` uses) - persisting/comparing them is the script's job, same as `.number()`.
	public KubeUIScreenBuilder keybindCapture(String id, int initial, BiConsumer<KubeUIContext, Integer> onChange) {
		return add(new KeybindCaptureElement(id, initial, safeBi(onChange)));
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

	/// Adds two side-by-side scrollable panes, `width` x `height` pixels total, separated by a
	/// draggable divider - `initialRatio` (clamped to `[0.1, 0.9]`) is the first pane's starting
	/// share of `width`. `first`/`second` each receive a nested builder for that pane's content.
	/// The ratio lives on this element and survives `rebuild()` (dragging the divider *is* what
	/// triggers one), the same way `.accordion(...)`'s expanded state does.
	public KubeUIScreenBuilder splitPane(int width, int height, double initialRatio, Consumer<KubeUIScreenBuilder> first, Consumer<KubeUIScreenBuilder> second) {
		var firstBuilder = childBuilder();
		first.accept(firstBuilder);
		var secondBuilder = childBuilder();
		second.accept(secondBuilder);
		return add(new SplitPaneElement(width, height, initialRatio, firstBuilder, secondBuilder));
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

	/// Adds a data table: a header row (`columnLabels`) plus one row per entry in `rows` (each a
	/// list of already-stringified cell values, in column order), columns aligned to
	/// `columnWidths`, alternating row background. Not sortable - see the overload with `onSort`.
	public KubeUIScreenBuilder table(String id, List<String> columnLabels, List<Integer> columnWidths, List<List<String>> rows) {
		return table(id, columnLabels, columnWidths, rows, null);
	}

	/// Same as [#table(String, List, List, List)], but clicking a column header fires
	/// `onSort(screen, columnIndex)` - unlike [#reorderableList], a table has no user-draggable
	/// internal order to update live, so actually sorting `rows` and showing the result is the
	/// script's job (typically `screen.update(b -> { b.remove(id); b.table(id, ..., sortedRows, onSort); })`).
	public KubeUIScreenBuilder table(String id, List<String> columnLabels, List<Integer> columnWidths, List<List<String>> rows, BiConsumer<KubeUIContext, Integer> onSort) {
		return add(new TableElement(columnLabels, columnWidths, rows, safeBi(onSort)));
	}

	/// Adds one row per entry in `items`, calling `renderer.render(row, item, index)` to build
	/// each one - same as [#list(String, List, KubeUIListItemRenderer)], with a drag handle
	/// prepended to every row so the player can reorder them by dragging. Rows actually move live
	/// as you drag (the display order is tracked internally and survives `rebuild()`, like
	/// `.accordion(...)`'s expanded state) - `onReorder(screen, from, to)` is purely a
	/// notification, fired once a drag ends having actually moved something, for a script that
	/// wants to mirror the new order into its own data (persistence, sending to a server, ...).
	public KubeUIScreenBuilder reorderableList(String id, List<?> items, KubeUIListItemRenderer renderer, KubeUIListReorderListener onReorder) {
		return add(new ReorderableListElement(id, items, renderer, safeReorder(onReorder)));
	}

	/// Adds one row per entry in `items` - same as [#list(String, List, KubeUIListItemRenderer)],
	/// with a checkbox prepended to every row for ctrl/shift-click multi-select (same modifier
	/// conventions as vanilla's creative inventory). `onSelectionChange` fires with the full set of
	/// selected indices (ascending) on every change; also readable any time via
	/// [KubeUIContext#getSelectedListItems].
	public KubeUIScreenBuilder selectableList(String id, List<?> items, KubeUIListItemRenderer renderer, BiConsumer<KubeUIContext, List<Integer>> onSelectionChange) {
		var state = new KubeUIListSelectionState();
		var safeOnSelectionChange = safeBi(onSelectionChange);

		grid(1, list -> {
			for (int i = 0; i < items.size(); i++) {
				Object item = items.get(i);
				int index = i;
				list.row(row -> {
					row.add(new ListSelectCheckboxElement(id, index, state, safeOnSelectionChange)).width(14);
					try {
						renderer.render(row, item, index);
					} catch (Exception e) {
						KubeUI.LOGGER.error("KubeUI: error rendering selectableList('{}', ...) item at index {}", id, index, e);
					}
				});
			}
		});

		return id(id);
	}

	/// Adds one row per entry in `items` - same as [#list(String, List, KubeUIListItemRenderer)],
	/// with a section-header label inserted whenever `groupOf(item)` differs from the previous
	/// item's group. `items` is assumed to already be in group-contiguous order - this doesn't sort it.
	public KubeUIScreenBuilder groupedList(String id, List<?> items, KubeUIListGroupClassifier groupOf, KubeUIListItemRenderer renderer) {
		grid(1, list -> {
			String lastGroup = null;
			boolean first = true;

			for (int i = 0; i < items.size(); i++) {
				Object item = items.get(i);
				int index = i;
				String group;
				try {
					group = groupOf.groupOf(item);
				} catch (Exception e) {
					KubeUI.LOGGER.error("KubeUI: error in groupedList('{}', ...) groupClassifier at index {}", id, index, e);
					group = null;
				}

				if (first || !java.util.Objects.equals(group, lastGroup)) {
					list.label(id + ":group:" + index, "— " + group + " —");
					lastGroup = group;
					first = false;
				}

				list.row(row -> {
					try {
						renderer.render(row, item, index);
					} catch (Exception e) {
						KubeUI.LOGGER.error("KubeUI: error rendering groupedList('{}', ...) item at index {}", id, index, e);
					}
				});
			}
		});

		return id(id);
	}

	/// Adds one row per entry in `items` - same as [#list(String, List, KubeUIListItemRenderer)],
	/// with a "Load more..." button after the last row that fires `onLoadMore`. For datasets too
	/// large to load all at once: `items` is only ever whatever the script currently has: growing it
	/// (fetch/compute more, then `screen.update(b -> { b.remove(id); b.paginatedList(id, allItemsSoFar, onLoadMore, renderer); })`)
	/// is the script's job. `onLoadMore` may be null (no button, e.g. once there's nothing left to load).
	public KubeUIScreenBuilder paginatedList(String id, List<?> items, Consumer<KubeUIContext> onLoadMore, KubeUIListItemRenderer renderer) {
		var safeOnLoadMore = safe(onLoadMore);

		grid(1, list -> {
			for (int i = 0; i < items.size(); i++) {
				Object item = items.get(i);
				int index = i;
				list.row(row -> {
					try {
						renderer.render(row, item, index);
					} catch (Exception e) {
						KubeUI.LOGGER.error("KubeUI: error rendering paginatedList('{}', ...) item at index {}", id, index, e);
					}
				});
			}

			if (safeOnLoadMore != null) {
				list.button("Load more...", safeOnLoadMore);
			}
		});

		return id(id);
	}

	/// Adds a collapsible hierarchy: one row per node in `rootNodes` (and, recursively, whatever
	/// `childrenOf` returns for each), each indented by depth with an expand/collapse toggle when
	/// it has children. `renderer.render(row, node, depth)` builds a node's own row content.
	/// Expand state is tracked internally (by node identity/equality) and survives `rebuild()`.
	public KubeUIScreenBuilder tree(String id, List<?> rootNodes, KubeUITreeChildrenSupplier childrenOf, KubeUIListItemRenderer renderer) {
		return add(new TreeElement(id, rootNodes, childrenOf, renderer));
	}

	/// Adds a simple bar or line chart (`kind` is `"bar"` or `"line"`) plotting `values`.
	public KubeUIScreenBuilder chart(String id, String kind, List<Double> values) {
		return chart(id, kind, values, null);
	}

	/// Same as [#chart(String, String, List)], with one label per value drawn underneath it.
	public KubeUIScreenBuilder chart(String id, String kind, List<Double> values, List<String> labels) {
		return add(new ChartElement(kind, values, labels));
	}

	/// Adds a simplified top-down minimap, always centered on the local player (like a
	/// Journeymap-style minimap - it re-centers itself as the player moves, see [KubeUIMinimap]),
	/// showing `radius` blocks in every direction. Colored the same way vanilla maps are (real
	/// per-block color) - sampled on a fixed-resolution grid rather than one real pixel per block,
	/// so cost doesn't scale with `radius` or the widget's own pixel size.
	public KubeUIScreenBuilder map(String id, int radius) {
		return add(new MapElement(radius));
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

	/// Adds a collapsible section - a clickable header (`title`) that shows/hides its content
	/// (`children`, a nested builder) below it. Unlike `.tab(...)`, this is a normal element -
	/// mix it freely with buttons/labels/etc. on the same builder.
	public KubeUIScreenBuilder accordion(String title, Consumer<KubeUIScreenBuilder> children) {
		return accordion(title, false, children);
	}

	/// Same as [#accordion(String, Consumer)], but starting expanded.
	public KubeUIScreenBuilder accordion(String title, boolean initiallyExpanded, Consumer<KubeUIScreenBuilder> children) {
		var nested = childBuilder();
		children.accept(nested);
		return add(new AccordionElement(title, nested, initiallyExpanded));
	}

	/// Adds a "Step1 > Step2 > Step3"-style trail. `onSelect(screen, index)` (may be null for a
	/// read-only trail) fires when a step other than the last is clicked - the last step is shown
	/// as the current, non-clickable one.
	public KubeUIScreenBuilder breadcrumb(List<String> steps, BiConsumer<KubeUIContext, Integer> onSelect) {
		return add(new BreadcrumbElement(steps, onSelect == null ? null : safeBi(onSelect)));
	}

	/// Adds a multi-step flow with Previous/Next navigation. `renderer.render(step, index)` is
	/// called to (re)build whichever step is currently active - once up front, and again every
	/// time the active step changes. All steps share this same builder's width/theme/etc.
	public KubeUIScreenBuilder wizard(List<String> stepNames, KubeUIWizardStepRenderer renderer) {
		return wizard(stepNames, renderer, null);
	}

	/// Same as [#wizard(List, KubeUIWizardStepRenderer)], but `canAdvance.test(index)` gates
	/// whether "Next" is allowed to leave step `index` (e.g. a required field not filled in yet).
	/// Checked against the step being *left*, not the one being entered.
	public KubeUIScreenBuilder wizard(List<String> stepNames, KubeUIWizardStepRenderer renderer, java.util.function.IntPredicate canAdvance) {
		var stepContents = new ArrayList<KubeUIScreenBuilder>();

		for (int i = 0; i < stepNames.size(); i++) {
			var nested = childBuilder();
			int index = i;
			try {
				renderer.render(nested, index);
			} catch (Exception ex) {
				KubeUI.LOGGER.error("KubeUI: error rendering wizard step {}", index, ex);
			}
			stepContents.add(nested);
		}

		return add(new WizardElement(stepNames, stepContents, canAdvance));
	}

	/// Adds a slider with two independently draggable handles, for picking an interval instead
	/// of one value. `onChange` fires (with the current low/high) while dragging either handle.
	public KubeUIScreenBuilder rangeSlider(String id, double min, double max, double initialLow, double initialHigh, KubeUIRangeChangeListener onChange) {
		return add(new RangeSliderElement(id, min, max, initialLow, initialHigh, onChange));
	}

	/// Adds a slider over a fixed, ordered list of labeled steps rather than a numeric range -
	/// the displayed text is the step's own label. `onChange` fires with the step's label.
	public KubeUIScreenBuilder steppedSlider(String id, List<String> steps, String initial, BiConsumer<KubeUIContext, String> onChange) {
		if (!requireNonEmptyOptions("steppedSlider", id, steps)) {
			return this;
		}
		return add(new SteppedSliderElement(id, steps, initial, safeBi(onChange)));
	}

	/// Adds a year/month/day date field (three number spinners under one id). `onChange` fires
	/// with the combined value formatted `"YYYY-MM-DD"` whenever any of the three changes.
	public KubeUIScreenBuilder datePicker(String id, int initialYear, int initialMonth, int initialDay, BiConsumer<KubeUIContext, String> onChange) {
		return add(new DatePickerElement(id, initialYear, initialMonth, initialDay, safeBi(onChange)));
	}

	/// Adds a text field that filters `options` as you type, showing matches in a list right
	/// below it (not a floating popup). Clicking a match selects it and fires `onChange`.
	public KubeUIScreenBuilder searchableDropdown(String id, List<String> options, String initial, BiConsumer<KubeUIContext, String> onChange) {
		if (!requireNonEmptyOptions("searchableDropdown", id, options)) {
			return this;
		}
		return add(new SearchableDropdownElement(id, options, initial, safeBi(onChange)));
	}

	/// Adds a compact multi-select: a button showing how many are selected, expanding to a
	/// checkbox list (like `.checkboxGroup(...)`, but collapsed by default). `onChange` fires
	/// with the full list of currently-checked options on every toggle.
	public KubeUIScreenBuilder multiSelectDropdown(String id, List<String> options, List<String> initialSelected, BiConsumer<KubeUIContext, List<String>> onChange) {
		if (!requireNonEmptyOptions("multiSelectDropdown", id, options)) {
			return this;
		}
		return add(new MultiSelectDropdownElement(id, options, initialSelected, safeBi(onChange)));
	}

	/// Adds a text field meant to filter a `.list(...)`/`.scrollPanel(...)` elsewhere on the same
	/// screen - `onQueryChange` only fires ~400ms after typing stops (debounced), not on every
	/// keystroke like `.textField(...)`, so filtering a big list doesn't rebuild on every letter.
	public KubeUIScreenBuilder searchBox(String id, BiConsumer<KubeUIContext, String> onQueryChange) {
		return add(new SearchBoxElement(id, safeBi(onQueryChange)));
	}

	/// Adds a filterable browser over every registered item id, with an icon preview per match -
	/// `kind` is currently only really "item" (icon shown); `"sound"`/`"texture"` list matching
	/// ids from those registries too, but without a preview icon.
	public KubeUIScreenBuilder resourcePicker(String id, String kind, String initial, BiConsumer<KubeUIContext, String> onChange) {
		List<String> allIds = KubeUIResourceIds.forKind(kind);
		return add(new ResourcePickerElement(id, kind, allIds, initial, safeBi(onChange)));
	}

	/// Registers a screen-wide keyboard shortcut, independent of Minecraft's own keybind system -
	/// pressing `key` while this screen is open fires `onTrigger(screen)`, regardless of which
	/// widget (if any) currently has focus. `key` is a single letter/digit (`"R"`), a function key
	/// (`"F1"`-`"F12"`), or one of `"escape"`/`"enter"`/`"space"`/`"tab"`/`"delete"`/`"backspace"`/
	/// `"up"`/`"down"`/`"left"`/`"right"` - anything else is rejected with a logged error instead of
	/// silently doing nothing. Root builder only.
	public KubeUIScreenBuilder hotkey(String key, Consumer<KubeUIContext> onTrigger) {
		Integer code = KubeUIKeyNames.resolve(key);
		if (code == null) {
			KubeUI.LOGGER.error("KubeUI: .hotkey('{}', ...) - unrecognized key name", key);
			return this;
		}
		hotkeys.put(code, safe(onTrigger));
		return this;
	}

	/// Adds a right-click menu to the last-added element. `onSelect(screen, item)` fires with
	/// whichever `items` entry was clicked.
	public KubeUIScreenBuilder contextMenu(List<String> items, BiConsumer<KubeUIContext, String> onSelect) {
		lastStyle().contextMenuItems = items;
		lastStyle().contextMenuOnSelect = safeBi(onSelect);
		return this;
	}

	/// Same as [#contextMenu(List, BiConsumer)], but `itemsSupplier(screen)` is called fresh every
	/// time the menu is opened instead of a fixed list baked in at build time - for a menu whose
	/// options depend on state that can change while the screen is open (a "recently used" list, a
	/// count in a label, ...).
	public KubeUIScreenBuilder contextMenu(java.util.function.Function<KubeUIContext, List<String>> itemsSupplier, BiConsumer<KubeUIContext, String> onSelect) {
		lastStyle().contextMenuItemsSupplier = safeContextMenuSupplier(itemsSupplier);
		lastStyle().contextMenuOnSelect = safeBi(onSelect);
		return this;
	}

	/// Makes the last-added element draggable, carrying `payload` (any script value - handed back
	/// as-is to a [#dropTarget(BiConsumer)]'s `onDrop`, never inspected by KubeUI itself) - the
	/// generic counterpart to [#reorderableList]'s built-in drag handles, for dragging between two
	/// *different* widgets/elements instead of reordering rows of the same list.
	public KubeUIScreenBuilder draggableFrom(Object payload) {
		lastStyle().dragPayload = payload;
		return this;
	}

	/// Marks the last-added element as a place a [#draggableFrom(Object)] element can be dropped
	/// on - `onDrop(screen, payload)` fires with whichever payload was being dragged when the mouse
	/// is released over it.
	public KubeUIScreenBuilder dropTarget(BiConsumer<KubeUIContext, Object> onDrop) {
		lastStyle().dropHandler = safeBi(onDrop);
		return this;
	}

	/// Adds local undo/redo (Ctrl+Z / Ctrl+Y) to the last-added `.textField(...)`, independent of
	/// whatever the OS-level text field undo (if any) would otherwise do - a plain snapshot stack
	/// of previous values, pushed on every change, capped at 50 entries.
	public KubeUIScreenBuilder undoable() {
		lastStyle().undoable = true;
		return this;
	}

	/// Fires `onDoubleClick(screen)` when the last-added element is double-clicked, in addition to
	/// (not instead of) whatever its normal single click already does.
	public KubeUIScreenBuilder onDoubleClick(Consumer<KubeUIContext> onDoubleClick) {
		lastStyle().onDoubleClick = safe(onDoubleClick);
		return this;
	}

	/// Shows a small popover built by `children` (a nested builder, positioned just below the
	/// element) after the last-added element has been continuously hovered for `delayMs` - for a
	/// richer preview than a plain `.tooltip(...)` string, without needing a click. Dismissed as
	/// soon as the mouse leaves.
	public KubeUIScreenBuilder hoverPreview(int delayMs, Consumer<KubeUIScreenBuilder> children) {
		lastStyle().hoverPreviewDelayMs = Math.max(1, delayMs);
		lastStyle().hoverPreviewBuilder = children;
		return this;
	}

	/// Chooses what's drawn behind the screen's content, replacing the default. `mode` is one of:
	/// `"dirt"` (the default - the same vanilla full-menu background every KubeUI screen has always
	/// had: the dirt-pattern texture, plus a real blur behind it if the *player's own* Menu
	/// Background Blurriness option is turned up - `.background(...)` was never actually needed to
	/// get this, it's just now an explicit, nameable choice instead of only ever being whatever
	/// `Screen`'s un-overridden default happened to be); `"blur"` (a real blur - see
	/// `GuiGraphicsExtractor#blurBeforeThisStratum` - forced on regardless of the player's own
	/// option, over a plain dark tint instead of the dirt texture); `"none"` (just the plain dark
	/// tint, no blur, no dirt - the lightest option). Root builder only.
	public KubeUIScreenBuilder background(String mode) {
		this.backgroundMode = mode;
		this.backgroundTexture = null;
		return this;
	}

	/// Same as [#background(String)], but a custom texture stretched to fill the screen instead of
	/// a named mode. Root builder only.
	public KubeUIScreenBuilder background(Identifier texture) {
		this.backgroundTexture = texture;
		this.backgroundMode = "texture";
		return this;
	}

	/// Makes the screen's content draggable by its title. Root builder only.
	public KubeUIScreenBuilder draggable() {
		this.draggable = true;
		return this;
	}

	/// While dragging a `.draggable()` window, pulls it flush against whichever screen edge it's
	/// released near (within 10px), desktop-window-manager style. No-op without `.draggable()`.
	/// Root builder only.
	public KubeUIScreenBuilder snapToEdges() {
		this.snapToEdges = true;
		return this;
	}

	/// Opens as one of possibly several *independent* KubeUI windows shown at once, instead of the
	/// usual one-`Minecraft.screen`-at-a-time replacement - see [KubeUIMultiWindowHost]. `.open()`
	/// adds this as a new window to whatever non-modal host is already showing (if any), or creates
	/// one if this is the first. Typically paired with `.draggable()` so the windows can actually be
	/// told apart/moved independently. Root builder only.
	public KubeUIScreenBuilder nonModal() {
		this.nonModal = true;
		return this;
	}

	/// Adds a collapse toggle to a `.draggable()` window's title bar - clicking it replaces the
	/// content with a thin placeholder (the window shrinks to just its title bar, draggable the
	/// same as always) instead of hiding the whole window. Requires `.draggable()`; ignored
	/// otherwise. Root builder only.
	public KubeUIScreenBuilder minimizable() {
		this.minimizable = true;
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

	/// A *real* visual scale for this one screen (clamped to `[0.5, 2.0]`) - unlike
	/// [#setScale(double)] (which multiplies computed widget/box sizes before layout, so text stays
	/// native size and can overflow its now-bigger box), this pushes an actual transform
	/// (`GuiGraphicsExtractor#pose`) around the screen's fully laid-out content, text included, and
	/// corrects incoming mouse coordinates by the same factor so clicking/hovering stays accurate.
	/// Stacks with [#setScale(double)] if both are used (rare, but not forbidden) - box sizes get
	/// multiplied first, then the whole already-bigger result gets visually scaled again. Root
	/// builder only.
	public KubeUIScreenBuilder renderScale(double factor) {
		this.renderScale = (float) Math.max(0.5, Math.min(2.0, factor));
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

	/// Same as [#animated(int)], with an animation `type` (`"fade"` - the default, unchanged;
	/// `"slide"` - content eases in from slightly below and back out the same way; `"scale"` -
	/// content grows in from 85% and shrinks back out) and/or an `easing` curve (`"linear"` - the
	/// default, unchanged; `"easeInOut"`) instead of the plain linear fade `.animated(int)` always
	/// used. Missing keys keep their default. Both `"slide"`/`"scale"` are drawn with a real
	/// transform (`GuiGraphicsExtractor#pose`, same mechanism as [#renderScale(double)]) around the
	/// content, not a fake/approximated effect - `"fade"` remains the only type that doesn't need
	/// one (`AbstractWidget#setAlpha` already does the job).
	public KubeUIScreenBuilder animated(Map<String, Object> options) {
		this.animated = true;
		Object duration = options.get("durationMs");
		if (duration instanceof Number n) {
			this.animationDurationMs = Math.max(1, n.intValue());
		}
		Object type = options.get("type");
		if (type instanceof String s && (s.equals("fade") || s.equals("slide") || s.equals("scale"))) {
			this.animationType = s;
		}
		Object easing = options.get("easing");
		if (easing instanceof String s && (s.equals("linear") || s.equals("easeInOut"))) {
			this.animationEasing = s;
		}
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

	/// Same as [#width(int)]/[#width(String)], but switches between a few pixel/percentage values
	/// depending on the current screen width instead of one fixed spec - a step beyond the plain
	/// percentage of [#width(String)]. `breakpoints` maps a tier name to a value (an `int` pixel
	/// count or a `"NN%"` string): `"default"` is used above 640 logical pixels, `"small"` between
	/// 400 and 640, `"tiny"` below 400 - a missing tier falls back to `"default"`. Re-resolved on
	/// every `rebuild()`/window resize, same as [#width(String)].
	public KubeUIScreenBuilder width(Map<String, Object> breakpoints) {
		lastStyle().responsiveWidth = breakpoints;
		lastStyle().width = null;
		lastStyle().widthPercent = null;
		return this;
	}

	/// Distributes leftover space in a `.row(...)` proportionally among children that ask for some
	/// (higher `weight` gets a bigger share), instead of leaving it as blank trailing space - like
	/// flexbox's `flex-grow`. Only has an effect if the row itself has an explicit `.width(...)`
	/// wider than the sum of its children's natural widths - there's no leftover space to
	/// redistribute otherwise. Ignored on anything that isn't a direct child of a `.row(...)`.
	public KubeUIScreenBuilder grow(int weight) {
		lastStyle().growWeight = Math.max(0, weight);
		return this;
	}

	/// Takes the last-added element out of the normal layout flow entirely and positions it at
	/// fixed `(x, y)` coordinates relative to the screen's content area instead - an escape hatch
	/// for placement `.row()`/`.grid()` can't express (e.g. a badge pinned to a corner regardless
	/// of everything else's layout). Doesn't reserve any space where it would otherwise have sat.
	public KubeUIScreenBuilder absolute(int x, int y) {
		lastStyle().absoluteX = x;
		lastStyle().absoluteY = y;
		return this;
	}

	/// Sets the paint order of the last-added element relative to other elements it overlaps
	/// (higher draws on top of lower) - only meaningful for elements placed with
	/// [#absolute(int, int)], since normal layout flow never overlaps by construction.
	public KubeUIScreenBuilder zIndex(int z) {
		lastStyle().zIndex = z;
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

	/// Same as [#tooltip(String)], but multiple styled lines (build each with vanilla
	/// `Component`/`Style`, same as [#richText]) instead of one flat string. Takes priority over
	/// `.tooltip(...)` if both are set on the same element.
	public KubeUIScreenBuilder richTooltip(List<Component> lines) {
		lastStyle().richTooltipLines = lines;
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
		if (nonModal) {
			var mc = Minecraft.getInstance();
			if (mc.screen instanceof KubeUIMultiWindowHost host) {
				host.addWindow(this);
			} else {
				var host = new KubeUIMultiWindowHost();
				host.addWindow(this);
				mc.setScreen(host);
			}
			return;
		}

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

	/// Registers `name` as a client-only command (typed as `/name`) that runs `callback` - the
	/// simplest way to open a screen without a keybind, block, or item, e.g.
	/// `KubeUI.registerCommand('shop', () => openShop())`. Takes effect the next time Minecraft
	/// (re)builds its client command list (on join, or `/reload`) - registering after that has
	/// already happened this session won't take effect until the next rebuild. `name` is
	/// registered as a top-level command exactly as given, so it's the script's own responsibility
	/// to pick something that won't collide with a vanilla or another mod's command.
	public static void registerCommand(String name, Runnable callback) {
		KubeUICommandRegistry.register(name, safeRunnable(callback));
	}

	/// Reserves `pixels` along `edge` (`"top"`/`"bottom"`/`"left"`/`"right"`) that a KubeUI screen
	/// `.anchor(...)`ed to that same edge should leave alone, for coexisting with another mod's
	/// persistent HUD/overlay pinned there - see [KubeUISafeArea]. Purely a registry KubeUI trusts;
	/// nothing here surveys what's actually on screen.
	public static void reserveSafeArea(String edge, int pixels) {
		KubeUISafeArea.reserve(edge, pixels);
	}

	/// Undoes [#reserveSafeArea(String, int)] for `edge`.
	public static void clearSafeArea(String edge) {
		KubeUISafeArea.clear(edge);
	}

	/// Shows a non-modal, auto-dismissing notification (top-right corner) that stacks with any
	/// others already showing, independent of whatever KubeUI screen (if any) is currently open -
	/// unlike [#alert(String, String, Runnable)], which is a modal dialog over a specific screen.
	public static void toast(String message, int durationMs) {
		KubeUIToast.add(message, durationMs);
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

	private static KubeUIListReorderListener safeReorder(KubeUIListReorderListener callback) {
		if (callback == null) {
			return null;
		}
		return (ctx, from, to) -> {
			try {
				callback.onReorder(ctx, from, to);
			} catch (Exception e) {
				KubeUI.LOGGER.error("KubeUI: error in a reorderableList() onReorder callback", e);
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

	private static java.util.function.Function<KubeUIContext, List<String>> safeContextMenuSupplier(java.util.function.Function<KubeUIContext, List<String>> supplier) {
		if (supplier == null) {
			return null;
		}
		return ctx -> {
			try {
				return supplier.apply(ctx);
			} catch (Exception e) {
				KubeUI.LOGGER.error("KubeUI: error in a contextMenu() items supplier", e);
				return List.of();
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

	/// Lets `KubeUIScreen` create a nested builder matching `parent`'s width/button-height
	/// conventions, for element types (like `.tree(...)`) whose visible rows depend on state that
	/// can change after the initial build and so need to be re-populated on every `rebuild()`,
	/// unlike `.tab(...)`/`.accordion(...)`, whose nested content is built once and reused as-is.
	static KubeUIScreenBuilder freshChildBuilder(KubeUIScreenBuilder parent) {
		return parent.childBuilder();
	}

	/// Lets `KubeUIScreen#buildReorderableList` add a drag handle to a row it's building fresh
	/// every `rebuild()` - not script-facing (there's no public `.dragHandle(...)`, this only
	/// makes sense wired to a specific [ReorderableListElement]'s live state).
	KubeUIScreenBuilder reorderHandle(int pos, KubeUIListDragState dragState, ReorderableListElement listElement) {
		return add(new ReorderHandleElement(pos, dragState, listElement));
	}

	/// Per-element layout/behavior overrides, mutated in place by width()/height()/align()/
	/// padding()/tooltip()/tabOrder()/narration()/id() - they always apply to whichever element
	/// was added most recently.
	static final class Style {
		Integer width;
		Integer height;
		Float widthPercent;
		Float heightPercent;
		Map<String, Object> responsiveWidth;
		Integer growWeight;
		Integer absoluteX;
		Integer absoluteY;
		Integer zIndex;
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
		List<String> contextMenuItems;
		java.util.function.Function<KubeUIContext, List<String>> contextMenuItemsSupplier;
		BiConsumer<KubeUIContext, String> contextMenuOnSelect;
		List<Component> richTooltipLines;
		Object dragPayload;
		BiConsumer<KubeUIContext, Object> dropHandler;
		boolean undoable;
		Consumer<KubeUIContext> onDoubleClick;
		Integer hoverPreviewDelayMs;
		Consumer<KubeUIScreenBuilder> hoverPreviewBuilder;
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

	record RichTextElement(String id, Component text, Consumer<KubeUIContext> onClick) implements Element {
	}

	record BadgeElement(String text, int color) implements Element {
	}

	record RatingElement(String id, int max, int initial, BiConsumer<KubeUIContext, Integer> onChange) implements Element {
	}

	record SpinnerElement(String id) implements Element {
	}

	record PanelBackgroundElement(Identifier texture) implements Element {
	}

	record EntityPreviewElement(EntityType<?> entityType) implements Element {
	}

	record KeybindCaptureElement(String id, int initial, BiConsumer<KubeUIContext, Integer> onChange) implements Element {
	}

	/// Not a record - `expanded` needs to survive `rebuild()` (toggling it *is* what triggers a
	/// rebuild), and this same instance is reused across rebuilds since `KubeUIScreen` re-walks
	/// the same `builder`/`entries` rather than recreating them.
	static final class AccordionElement implements Element {
		final String title;
		final KubeUIScreenBuilder content;
		boolean expanded;

		AccordionElement(String title, KubeUIScreenBuilder content, boolean expanded) {
			this.title = title;
			this.content = content;
			this.expanded = expanded;
		}
	}

	record BreadcrumbElement(List<String> steps, BiConsumer<KubeUIContext, Integer> onSelect) implements Element {
	}

	/// Same "not a record" reasoning as [AccordionElement] - `currentStep` needs to survive
	/// `rebuild()`. `stepContents` is built eagerly (one nested builder per step, populated via
	/// the renderer up front) rather than lazily per active step, the same way `.tab(...)` builds
	/// every tab's content eagerly - simpler than re-invoking a script callback from inside
	/// `KubeUIScreen`, and avoids needing `childBuilder()` to be visible outside this class.
	static final class WizardElement implements Element {
		final List<String> stepNames;
		final List<KubeUIScreenBuilder> stepContents;
		final java.util.function.IntPredicate canAdvance;
		int currentStep;

		WizardElement(List<String> stepNames, List<KubeUIScreenBuilder> stepContents, java.util.function.IntPredicate canAdvance) {
			this.stepNames = stepNames;
			this.stepContents = stepContents;
			this.canAdvance = canAdvance;
		}
	}

	record RangeSliderElement(String id, double min, double max, double initialLow, double initialHigh, KubeUIRangeChangeListener onChange) implements Element {
	}

	record SteppedSliderElement(String id, List<String> steps, String initial, BiConsumer<KubeUIContext, String> onChange) implements Element {
	}

	record DatePickerElement(String id, int initialYear, int initialMonth, int initialDay, BiConsumer<KubeUIContext, String> onChange) implements Element {
	}

	/// Mutable - `query`/`selected` need to survive `rebuild()` (typing or picking a match *is*
	/// what triggers one). No floating popup - the filtered matches show as a plain list directly
	/// below the search field, always in the layout flow rather than an overlay on top of it.
	static final class SearchableDropdownElement implements Element {
		final String id;
		final List<String> options;
		final String initial;
		final BiConsumer<KubeUIContext, String> onChange;
		String query = "";
		String selected;

		SearchableDropdownElement(String id, List<String> options, String initial, BiConsumer<KubeUIContext, String> onChange) {
			this.id = id;
			this.options = options;
			this.initial = initial;
			this.selected = initial;
			this.onChange = onChange;
		}
	}

	/// Mutable for the same reason as [SearchableDropdownElement] - `expanded`/`selected` need to
	/// survive `rebuild()`.
	static final class MultiSelectDropdownElement implements Element {
		final String id;
		final List<String> options;
		final BiConsumer<KubeUIContext, List<String>> onChange;
		final List<String> selected;
		boolean expanded;

		MultiSelectDropdownElement(String id, List<String> options, List<String> initialSelected, BiConsumer<KubeUIContext, List<String>> onChange) {
			this.id = id;
			this.options = options;
			this.selected = new ArrayList<>(initialSelected);
			this.onChange = onChange;
		}
	}

	/// Mutable - `query`/timing state needs to survive `rebuild()`, and is updated every
	/// keystroke without one (see `KubeUIScreen#tick` for the debounce check).
	static final class SearchBoxElement implements Element {
		final String id;
		final BiConsumer<KubeUIContext, String> onQueryChange;
		String query = "";
		long lastKeystrokeAt;
		boolean pending;

		SearchBoxElement(String id, BiConsumer<KubeUIContext, String> onQueryChange) {
			this.id = id;
			this.onQueryChange = onQueryChange;
		}
	}

	/// Same shape/reasoning as [SearchableDropdownElement], but rows show an item icon (when
	/// resolvable) alongside the id, and only `"item"` actually resolves a preview - `"sound"`/
	/// `"texture"` just list matching ids without one (see `KubeUIScreen#buildResourcePicker`).
	static final class ResourcePickerElement implements Element {
		final String id;
		final String kind;
		final List<String> allIds;
		final BiConsumer<KubeUIContext, String> onChange;
		String query = "";
		String selected;

		ResourcePickerElement(String id, String kind, List<String> allIds, String initial, BiConsumer<KubeUIContext, String> onChange) {
			this.id = id;
			this.kind = kind;
			this.allIds = allIds;
			this.selected = initial;
			this.onChange = onChange;
		}
	}

	record TableElement(List<String> columnLabels, List<Integer> columnWidths, List<List<String>> rows, BiConsumer<KubeUIContext, Integer> onSort) implements Element {
	}

	/// Not a record - `order` needs to survive `rebuild()` *and* changes as the sole effect of a
	/// drag in progress (there's no re-invocation of `.reorderableList(...)` involved, unlike
	/// `.table()`'s `onSort`/`.paginatedList()`'s `onLoadMore`) - same reasoning as [TreeElement].
	/// `order` is a permutation of `[0, items.size())`: `order.get(displayPosition)` is the index
	/// into `items` currently shown at that position.
	record ReorderHandleElement(int pos, KubeUIListDragState dragState, ReorderableListElement listElement) implements Element {
	}

	static final class ReorderableListElement implements Element {
		final String id;
		final List<?> items;
		final KubeUIListItemRenderer renderer;
		final KubeUIListReorderListener onReorder;
		final List<Integer> order = new ArrayList<>();
		final KubeUIListDragState dragState = new KubeUIListDragState();

		ReorderableListElement(String id, List<?> items, KubeUIListItemRenderer renderer, KubeUIListReorderListener onReorder) {
			this.id = id;
			this.items = items;
			this.renderer = renderer;
			this.onReorder = onReorder;
			for (int i = 0; i < items.size(); i++) {
				order.add(i);
			}
		}
	}

	record ListSelectCheckboxElement(String listId, int index, KubeUIListSelectionState state, BiConsumer<KubeUIContext, List<Integer>> onSelectionChange) implements Element {
	}

	record ChartElement(String kind, List<Double> values, List<String> labels) implements Element {
	}

	record MapElement(int radius) implements Element {
	}

	/// Not a record - `expanded` needs to survive `rebuild()` (an expand/collapse toggle *is* what
	/// triggers one), and unlike every other composite here, a tree's visible rows genuinely depend
	/// on that state, so they're rebuilt fresh (via `KubeUIScreen#buildTree`) every `rebuild()`
	/// rather than being decided once up front like `.tab(...)`/`.accordion(...)`'s content.
	static final class TreeElement implements Element {
		final String id;
		final List<?> rootNodes;
		final KubeUITreeChildrenSupplier childrenOf;
		final KubeUIListItemRenderer renderer;
		final Map<Object, Boolean> expanded = new HashMap<>();

		TreeElement(String id, List<?> rootNodes, KubeUITreeChildrenSupplier childrenOf, KubeUIListItemRenderer renderer) {
			this.id = id;
			this.rootNodes = rootNodes;
			this.childrenOf = childrenOf;
			this.renderer = renderer;
		}
	}

	/// Not a record - same reasoning as [TreeElement]: dragging the divider *is* what triggers a
	/// rebuild, and `ratio` needs to survive it.
	static final class SplitPaneElement implements Element {
		final int width;
		final int height;
		final KubeUIScreenBuilder first;
		final KubeUIScreenBuilder second;
		double ratio;

		SplitPaneElement(int width, int height, double initialRatio, KubeUIScreenBuilder first, KubeUIScreenBuilder second) {
			this.width = width;
			this.height = height;
			this.ratio = Math.max(0.1, Math.min(0.9, initialRatio));
			this.first = first;
			this.second = second;
		}
	}
}
