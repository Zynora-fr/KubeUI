package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Screen rendered from a [KubeUIScreenBuilder]. Its tree of elements (including nested
/// row/grid/scrollPanel/tab containers) is compiled into a tree of vanilla [GridLayout] /
/// [ScrollableLayout] instances, which handle sizing, padding, alignment and scrolling - KubeUI
/// only builds the leaf widgets and wires them into that tree. Stateful widgets are tracked by id
/// so a [KubeUIContext] handed to callbacks can read/update them live, and can trigger a full
/// [#rebuild()] (tab switches, [KubeUIContext#update]).
///
/// Every `KubeUIContext` setter (`setLabel`, `setProgress`, `setEnabled`, ...) mutates only the
/// widget it targets - none of them call [#rebuild()]. `KubeUIContext#update` is the sole
/// exception: adding/removing elements fundamentally changes the widget tree, so it necessarily
/// rebuilds everything.
public class KubeUIScreen extends Screen {
	private static final int SPACING = 4;
	private static final int LABEL_HEIGHT = 12;
	private static final int FIELD_HEIGHT = 20;
	private static final int PROGRESS_HEIGHT = 14;
	private static final int DIVIDER_HEIGHT = 9;
	private static final int SWATCH_SIZE = 16;
	private static final int TITLE_BAR_HEIGHT = 16;
	private static final int NON_DRAGGABLE_TITLE_RESERVE = 24; // room for the fixed title text drawn at y=12 (see extractRenderState)
	private static final int RESIZE_HANDLE_SIZE = 8;
	private static final int[] COLOR_PALETTE = {
		0xFFFFFF, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xAA00AA, 0x999999, 0x000000
	};

	final KubeUIScreenBuilder builder;
	private final KubeUIContext context = new KubeUIContext(this);

	final Map<String, StringWidget> labels = new HashMap<>();
	final Map<String, EditBox> textFields = new HashMap<>();
	final Map<String, MultiLineEditBox> textAreas = new HashMap<>();
	final Map<String, KubeUISlider> sliders = new HashMap<>();
	final Map<String, CycleButton<String>> dropdowns = new HashMap<>();
	final Map<String, RadioGroupHandle> radioGroups = new HashMap<>();
	final Map<String, NumberHandle> numbers = new HashMap<>();
	final Map<String, ColorPickerHandle> colorPickers = new HashMap<>();
	final Map<String, CheckboxGroupHandle> checkboxGroups = new HashMap<>();
	final Map<String, KubeUIProgressBar> progressBars = new HashMap<>();
	final Map<String, Checkbox> toggles = new HashMap<>();
	final Map<String, LayoutElement> byId = new HashMap<>();

	private String activeTab;
	private Layout root;
	private int baseX;
	private int baseY;
	private int dragOffsetX;
	private int dragOffsetY;
	private ScreenRectangle titleBarRect = ScreenRectangle.empty();
	private boolean draggingWindow;
	private double dragStartMouseX;
	private double dragStartMouseY;
	private int dragStartOffsetX;
	private int dragStartOffsetY;

	private ScrollableLayout resizableLayout;
	private KubeUIResizeHandle resizeHandle;
	private Integer resizeCurrentHeight;

	private final List<AbstractWidget> allWidgets = new ArrayList<>();
	private long openStartTime;
	private boolean closing;
	private long closeStartTime;

	KubeUIScreen(KubeUIScreenBuilder builder) {
		super(builder.title);
		this.builder = builder;
	}

	@Override
	protected void init() {
		super.init();

		if (activeTab == null && !builder.tabs.isEmpty()) {
			activeTab = builder.tabs.get(0).name();
		}

		openStartTime = System.currentTimeMillis();
		rebuild();
		KubeUIDebug.trackOpened(this);

		if (builder.onOpenCallback != null) {
			builder.onOpenCallback.accept(context);
		}

		KubeUIEvents.SCREEN_OPEN.post(ScriptType.CLIENT, new KubeUIScreenEvent(context, builder.title.getString()));
	}

	/// Human-readable summary for `/kubeui debug` - not meant to be parsed.
	String debugSummary() {
		var sb = new StringBuilder();
		sb.append("Title: ").append(getTitle().getString()).append('\n');
		sb.append("Tabs: ").append(builder.tabs.isEmpty() ? "none" : builder.tabs.size() + " (active: " + activeTab + ")").append('\n');
		sb.append("Draggable: ").append(builder.draggable).append(", Resizable: ").append(builder.resizableMaxHeight != null).append('\n');
		sb.append("Animated: ").append(builder.animated).append(", PersistKey: ").append(builder.persistKey).append('\n');
		sb.append("Widgets with an id - labels: ").append(labels.size())
			.append(", textFields: ").append(textFields.size())
			.append(", textAreas: ").append(textAreas.size())
			.append(", sliders: ").append(sliders.size())
			.append(", dropdowns: ").append(dropdowns.size())
			.append(", radioGroups: ").append(radioGroups.size())
			.append(", numbers: ").append(numbers.size())
			.append(", colorPickers: ").append(colorPickers.size())
			.append(", checkboxGroups: ").append(checkboxGroups.size())
			.append(", toggles: ").append(toggles.size())
			.append(", progressBars: ").append(progressBars.size())
			.append('\n');
		sb.append("Total ids registered (byId, incl. .id(...) overrides): ").append(byId.size()).append(" -> ").append(byId.keySet());
		return sb.toString();
	}

	/// Fully re-lays-out and re-creates every widget from `builder` (and the active tab, if any),
	/// preserving drag offset and resize height. Triggered by tab switches and
	/// [KubeUIContext#update] - never by a plain state change (see class docs).
	void rebuild() {
		clearWidgets();
		clearState();

		Layout content = !builder.tabs.isEmpty() ? buildTabbedContent() : buildContainer(builder, 1);
		Layout positioned = content;

		int marginX = builder.anchorMarginX;
		int marginY = builder.anchorMarginY;
		// The title (drawn separately - see extractRenderState) is either fixed near the top of the
		// screen (non-draggable) or a bar directly above the content (draggable) - either way,
		// content needs to start below it, or a vertically-centered/top-anchored screen ends up with
		// its own text rendered right underneath/through the title.
		int topReserve = builder.draggable ? TITLE_BAR_HEIGHT : NON_DRAGGABLE_TITLE_RESERVE;

		if (builder.resizableMaxHeight != null) {
			int initialHeight = resizeCurrentHeight != null ? resizeCurrentHeight : builder.resizableMaxHeight;
			var scrollable = new ScrollableLayout(Minecraft.getInstance(), content, initialHeight);
			scrollable.setMinWidth(builder.elementWidth);

			if (builder.resizableMinHeight != null) {
				scrollable.setMinHeight(builder.resizableMinHeight);
			}

			resizableLayout = scrollable;
			positioned = scrollable;
		} else {
			resizableLayout = null;

			// Safety net for screens the scripter never made explicitly `.resizable()`: measure
			// the content's natural height, and if it's taller than what's actually available on
			// *this* window/resolution (which varies player to player), auto-wrap it in a scroll
			// area instead of letting it render off the bottom of the screen and become
			// unreachable - the same "never truly cut off" behavior vanilla Minecraft screens have.
			content.arrangeElements();
			int availableHeight = Math.max(RESIZE_HANDLE_SIZE, height - marginY * 2 - topReserve);

			if (content.getHeight() > availableHeight) {
				var autoScroll = new ScrollableLayout(Minecraft.getInstance(), content, availableHeight);
				autoScroll.setMinWidth(builder.elementWidth);
				positioned = autoScroll;
			}
		}

		positioned.arrangeElements();
		int areaWidth = Math.max(0, width - marginX * 2);
		int areaHeight = Math.max(0, height - marginY * 2 - topReserve);
		FrameLayout.alignInRectangle(positioned, marginX, marginY + topReserve, areaWidth, areaHeight, builder.anchorX, builder.anchorY);

		baseX = positioned.getX();
		baseY = positioned.getY();
		positioned.setX(baseX + dragOffsetX);
		positioned.setY(baseY + dragOffsetY);

		root = positioned;

		if (resizableLayout != null) {
			resizeCurrentHeight = resizableLayout.getHeight();
			resizeHandle = new KubeUIResizeHandle(
				root.getX() + root.getWidth() - RESIZE_HANDLE_SIZE,
				root.getY() + root.getHeight() - RESIZE_HANDLE_SIZE,
				RESIZE_HANDLE_SIZE,
				this::onResizeDrag
			);
		} else {
			resizeHandle = null;
		}

		titleBarRect = builder.draggable
			? new ScreenRectangle(new ScreenPosition(root.getX(), root.getY() - TITLE_BAR_HEIGHT), root.getWidth(), TITLE_BAR_HEIGHT)
			: ScreenRectangle.empty();

		root.visitWidgets(this::addRenderableWidget);

		if (resizeHandle != null) {
			addRenderableWidget(resizeHandle);
		}

		allWidgets.clear();
		root.visitWidgets(allWidgets::add);
		if (resizeHandle != null) {
			allWidgets.add(resizeHandle);
		}
	}

	private void clearState() {
		labels.clear();
		textFields.clear();
		textAreas.clear();
		sliders.clear();
		dropdowns.clear();
		radioGroups.clear();
		numbers.clear();
		colorPickers.clear();
		checkboxGroups.clear();
		progressBars.clear();
		toggles.clear();
		byId.clear();
	}

	@Override
	public void tick() {
		super.tick();

		for (var binding : builder.bindings) {
			Object value = binding.supplier.get();
			if (!Objects.equals(value, binding.lastValue)) {
				binding.lastValue = value;
				binding.onChange.accept(context, value);
			}
		}

		if (closing && System.currentTimeMillis() - closeStartTime >= builder.animationDurationMs) {
			Minecraft.getInstance().setScreen(null);
		}
	}

	@Override
	public void removed() {
		super.removed();

		if (builder.persistKey != null) {
			savePersistedState();
		}

		if (builder.onCloseCallback != null) {
			builder.onCloseCallback.accept(context);
		}

		KubeUIEvents.SCREEN_CLOSE.post(ScriptType.CLIENT, new KubeUIScreenEvent(context, builder.title.getString()));
	}

	/// Starts closing the screen - immediately if `.animated()` wasn't requested, or after a fade
	/// out otherwise. Used by both `screen.close()` and Esc (see [#onClose]).
	void requestClose() {
		if (!builder.animated) {
			Minecraft.getInstance().setScreen(null);
		} else if (!closing) {
			closing = true;
			closeStartTime = System.currentTimeMillis();
		}
	}

	@Override
	public void onClose() {
		requestClose();
	}

	private void savePersistedState() {
		if (builder.persistKey == null) {
			return;
		}

		var state = new HashMap<String, Object>();
		textFields.forEach((id, w) -> state.put(id, w.getValue()));
		textAreas.forEach((id, w) -> state.put(id, w.getValue()));
		sliders.forEach((id, w) -> state.put(id, w.currentValue()));
		dropdowns.forEach((id, w) -> state.put(id, w.getValue()));
		radioGroups.forEach((id, h) -> state.put(id, h.selected));
		numbers.forEach((id, h) -> state.put(id, h.value));
		colorPickers.forEach((id, h) -> state.put(id, h.color));
		checkboxGroups.forEach((id, h) -> state.put(id, selectedOf(h)));
		toggles.forEach((id, w) -> state.put(id, w.selected()));

		KubeUIScreenBuilder.PERSISTED.put(builder.persistKey, state);
	}

	private Object persisted(String id) {
		if (builder.persistKey == null || id == null) {
			return null;
		}
		var state = KubeUIScreenBuilder.PERSISTED.get(builder.persistKey);
		return state != null ? state.get(id) : null;
	}

	private Layout buildTabbedContent() {
		var wrapper = new GridLayout().rowSpacing(SPACING);

		var tabBar = new GridLayout().columnSpacing(SPACING);
		var tabBarHelper = tabBar.createRowHelper(builder.tabs.size());
		int scaledElementWidth = Math.max(1, Math.round(builder.elementWidth * KubeUITheme.uiScale));
		int scaledButtonHeight = Math.max(1, Math.round(builder.buttonHeight * KubeUITheme.uiScale));
		int tabWidth = Math.max(60, scaledElementWidth / Math.max(1, builder.tabs.size()));

		for (var tab : builder.tabs) {
			boolean active = tab.name().equals(activeTab);
			String text = active ? "[" + tab.name() + "]" : tab.name();

			var btn = Button.builder(styledText(text), b -> {
				activeTab = tab.name();
				rebuild();
			}).bounds(0, 0, tabWidth, scaledButtonHeight).build();

			tabBarHelper.addChild(btn);
		}

		wrapper.addChild(tabBar, 0, 0);

		var activeContent = builder.tabs.stream()
			.filter(tab -> tab.name().equals(activeTab))
			.findFirst()
			.map(KubeUIScreenBuilder.Tab::content)
			.orElse(builder.tabs.get(0).content());

		wrapper.addChild(buildContainer(activeContent, 1), 1, 0);
		return wrapper;
	}

	/// Builds a grid for `owner`'s entries. `columns` controls wrapping: 1 for a plain vertical
	/// stack, a fixed count for `.grid(n, ...)`, or a value greater than the entry count for
	/// `.row(...)` (a single row that never wraps).
	private Layout buildContainer(KubeUIScreenBuilder owner, int columns) {
		var grid = new GridLayout().spacing(SPACING);
		var rowHelper = grid.createRowHelper(Math.max(1, columns));

		for (var entry : owner.entries) {
			LayoutElement child = buildElement(owner, entry);
			applyCommonStyle(child, entry.style);

			String id = entry.effectiveId();
			if (id != null) {
				byId.put(id, child);
			}

			var settings = rowHelper.newCellSettings()
				.align(entry.style.alignX, entry.style.alignY)
				.padding(entry.style.paddingLeft, entry.style.paddingTop, entry.style.paddingRight, entry.style.paddingBottom);
			rowHelper.addChild(child, settings);
		}

		return grid;
	}

	private void applyCommonStyle(LayoutElement element, KubeUIScreenBuilder.Style style) {
		if (style.tooltip != null) {
			element.visitWidgets(w -> w.setTooltip(Tooltip.create(Component.literal(style.tooltip))));
		}

		if (style.tabOrder != null) {
			element.visitWidgets(w -> w.setTabOrderGroup(style.tabOrder));
		}

		if (style.narration != null) {
			element.visitWidgets(w -> {
				if (w instanceof KubeUINarratable narratable) {
					narratable.setCustomNarration(style.narration);
				}
			});
		}
	}

	private LayoutElement buildElement(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry) {
		return switch (entry.element) {
			case KubeUIScreenBuilder.RowElement e -> buildContainer(e.content(), e.content().entries.size() + 1);
			case KubeUIScreenBuilder.GridElement e -> buildContainer(e.content(), e.columns());
			case KubeUIScreenBuilder.ScrollPanelElement e -> {
				var content = buildContainer(e.content(), 1);
				var scrollable = new ScrollableLayout(Minecraft.getInstance(), content, e.maxHeight());
				scrollable.setMinWidth(e.content().elementWidth);
				yield scrollable;
			}
			case KubeUIScreenBuilder.ButtonElement e -> {
				int w = resolveWidth(entry, owner.elementWidth);
				int h = resolveHeight(entry, owner.buttonHeight);
				Identifier sound = entry.style.clickSound;
				yield Button.builder(styledText(e.text()), b -> {
					e.onClick().accept(context);
					playSound(sound);
				}).bounds(0, 0, w, h).build();
			}
			case KubeUIScreenBuilder.LabelElement e -> {
				var widget = new StringWidget(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, LABEL_HEIGHT), styledText(e.text()), font);
				labels.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.ToggleElement e -> {
				Identifier sound = entry.style.clickSound;
				Object saved = persisted(e.id());
				boolean initial = saved instanceof Boolean b ? b : e.initial();

				var widget = Checkbox.builder(styledText(e.text()), font)
					.pos(0, 0)
					.maxWidth(resolveWidth(entry, owner.elementWidth))
					.selected(initial)
					.onValueChange((checkbox, value) -> {
						e.onChange().accept(context, value);
						playSound(sound);
					})
					.build();

				toggles.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.TextFieldElement e -> {
				var widget = new EditBox(font, 0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, FIELD_HEIGHT), Component.literal(e.id()));
				Object saved = persisted(e.id());
				widget.setValue(saved instanceof String s ? s : e.initialValue());

				if (e.hint() != null && !e.hint().isEmpty()) {
					widget.setHint(Component.literal(e.hint()));
				}

				widget.setResponder(value -> e.onChange().accept(context, value));
				textFields.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.SliderElement e -> {
				Object saved = persisted(e.id());
				double initial = saved instanceof Number n ? n.doubleValue() : e.initial();
				var widget = new KubeUISlider(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, owner.buttonHeight), e.min(), e.max(), initial, context, e.onChange());
				sliders.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.DropdownElement e -> {
				Object saved = persisted(e.id());
				String initial = saved instanceof String s && e.options().contains(s) ? s
					: e.initial() != null && e.options().contains(e.initial()) ? e.initial() : e.options().get(0);
				var widget = CycleButton.builder(Component::literal, initial)
					.withValues(e.options())
					.displayOnlyValue()
					.create(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, owner.buttonHeight), Component.literal(e.id()), (button, value) -> e.onChange().accept(context, value));
				dropdowns.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.TextAreaElement e -> {
				var widget = MultiLineEditBox.builder()
					.setPlaceholder(e.hint() != null ? Component.literal(e.hint()) : Component.empty())
					.build(font, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, e.height()), Component.literal(e.id()));
				Object saved = persisted(e.id());
				widget.setValue(saved instanceof String s ? s : e.initialValue());
				widget.setValueListener(value -> e.onChange().accept(context, value));
				textAreas.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.ImageElement e ->
				new KubeUIImageWidget(0, 0, resolveWidth(entry, e.width()), resolveHeight(entry, e.height()), e.texture());
			case KubeUIScreenBuilder.ItemElement e -> {
				Identifier sound = entry.style.clickSound;
				var onClick = e.onClick();
				yield new KubeUIItemWidget(0, 0, e.stack(), font, onClick == null ? null : ev -> {
					onClick.accept(context);
					playSound(sound);
				});
			}
			case KubeUIScreenBuilder.ProgressBarElement e -> {
				var widget = new KubeUIProgressBar(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, PROGRESS_HEIGHT), e.value(), e.max(), font);
				progressBars.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.DividerElement ignored ->
				new KubeUIDivider(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, DIVIDER_HEIGHT));
			case KubeUIScreenBuilder.SpacerElement e -> new KubeUISpacer(resolveWidth(entry, owner.elementWidth), e.height());
			case KubeUIScreenBuilder.NumberElement e -> buildNumberSpinner(owner, entry, e);
			case KubeUIScreenBuilder.ColorPickerElement e -> buildColorPicker(owner, entry, e);
			case KubeUIScreenBuilder.RadioGroupElement e -> buildRadioGroup(owner, entry, e);
			case KubeUIScreenBuilder.CheckboxGroupElement e -> buildCheckboxGroup(owner, entry, e);
			case KubeUIScreenBuilder.CustomElement e -> buildCustom(owner, entry, e);
		};
	}

	private LayoutElement buildCustom(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.CustomElement e) {
		var factory = KubeUIWidgets.get(e.type());

		if (factory == null) {
			int w = resolveWidth(entry, owner.elementWidth);
			return new StringWidget(0, 0, w, LABEL_HEIGHT, Component.literal("Unknown custom widget: " + e.type()).withStyle(net.minecraft.ChatFormatting.RED), font);
		}

		var ctx = new KubeUIWidgetFactoryContext(
			font,
			context,
			resolveWidth(entry, owner.elementWidth),
			resolveHeight(entry, owner.buttonHeight),
			e.args()
		);

		return factory.create(ctx);
	}

	private LayoutElement buildNumberSpinner(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.NumberElement e) {
		int totalWidth = resolveWidth(entry, owner.elementWidth);
		int height = resolveHeight(entry, FIELD_HEIGHT);
		int buttonSize = height;
		int fieldWidth = Math.max(20, totalWidth - buttonSize * 2 - SPACING * 2);

		var field = new EditBox(font, 0, 0, fieldWidth, height, Component.literal(e.id()));
		Object saved = persisted(e.id());
		int initial = KubeUILayoutMath.clampInt(saved instanceof Number n ? n.intValue() : e.initial(), e.min(), e.max());
		field.setValue(String.valueOf(initial));

		var handle = new NumberHandle(e.min(), e.max(), initial, field);
		numbers.put(e.id(), handle);

		field.setResponder(text -> {
			Integer parsed = KubeUILayoutMath.parseInt(text);
			if (parsed != null) {
				handle.value = KubeUILayoutMath.clampInt(parsed, e.min(), e.max());
				e.onChange().accept(context, handle.value);
			}
		});

		var minus = Button.builder(Component.literal("-"), b -> {
			handle.value = KubeUILayoutMath.clampInt(handle.value - 1, e.min(), e.max());
			field.setValue(String.valueOf(handle.value));
			e.onChange().accept(context, handle.value);
		}).bounds(0, 0, buttonSize, height).build();

		var plus = Button.builder(Component.literal("+"), b -> {
			handle.value = KubeUILayoutMath.clampInt(handle.value + 1, e.min(), e.max());
			field.setValue(String.valueOf(handle.value));
			e.onChange().accept(context, handle.value);
		}).bounds(0, 0, buttonSize, height).build();

		var grid = new GridLayout().columnSpacing(SPACING);
		grid.addChild(minus, 0, 0);
		grid.addChild(field, 0, 1);
		grid.addChild(plus, 0, 2);
		return grid;
	}

	private LayoutElement buildRadioGroup(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.RadioGroupElement e) {
		int width = resolveWidth(entry, owner.elementWidth);
		int rowHeight = owner.buttonHeight;

		Object saved = persisted(e.id());
		String initialSelected = saved instanceof String s && e.options().contains(s) ? s
			: e.initial() != null && e.options().contains(e.initial()) ? e.initial() : e.options().get(0);

		var handle = new RadioGroupHandle(e.options(), initialSelected);
		radioGroups.put(e.id(), handle);

		var grid = new GridLayout().rowSpacing(SPACING);

		for (int i = 0; i < e.options().size(); i++) {
			String option = e.options().get(i);

			var btn = Button.builder(radioLabel(option, option.equals(handle.selected)), b -> {
				handle.selected = option;
				for (int j = 0; j < handle.buttons.size(); j++) {
					handle.buttons.get(j).setMessage(radioLabel(handle.options.get(j), handle.options.get(j).equals(option)));
				}
				e.onChange().accept(context, option);
			}).bounds(0, 0, width, rowHeight).build();

			handle.buttons.add(btn);
			grid.addChild(btn, i, 0);
		}

		return grid;
	}

	private LayoutElement buildCheckboxGroup(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.CheckboxGroupElement e) {
		int width = resolveWidth(entry, owner.elementWidth);

		Object saved = persisted(e.id());
		@SuppressWarnings("unchecked")
		List<String> initialSelected = saved instanceof List<?> l ? (List<String>) l : e.initialSelected();

		var handle = new CheckboxGroupHandle(e.options());
		checkboxGroups.put(e.id(), handle);

		var grid = new GridLayout().rowSpacing(SPACING);

		for (int i = 0; i < e.options().size(); i++) {
			String option = e.options().get(i);

			var box = Checkbox.builder(Component.literal(option), font)
				.pos(0, 0)
				.maxWidth(width)
				.selected(initialSelected != null && initialSelected.contains(option))
				.onValueChange((checkbox, value) -> e.onChange().accept(context, selectedOf(handle)))
				.build();

			handle.boxes.add(box);
			grid.addChild(box, i, 0);
		}

		return grid;
	}

	private LayoutElement buildColorPicker(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.ColorPickerElement e) {
		int width = resolveWidth(entry, owner.elementWidth);
		int count = COLOR_PALETTE.length;

		Object saved = persisted(e.id());
		int initialColor = saved instanceof Number n ? n.intValue() : e.initial();

		var hexField = new EditBox(font, 0, 0, width, FIELD_HEIGHT, Component.literal(e.id()));
		hexField.setMaxLength(9);
		hexField.setValue(KubeUILayoutMath.formatHexColor(initialColor));

		var handle = new ColorPickerHandle(hexField);
		handle.color = initialColor;
		colorPickers.put(e.id(), handle);

		var grid = new GridLayout().columnSpacing(2).rowSpacing(SPACING);
		var rowHelper = grid.createRowHelper(count);

		for (int paletteColor : COLOR_PALETTE) {
			var swatch = new KubeUIColorSwatch(0, 0, SWATCH_SIZE, paletteColor, picked -> {
				int argb = 0xFF000000 | picked;
				handle.color = argb;
				hexField.setValue(KubeUILayoutMath.formatHexColor(picked));
				e.onChange().accept(context, argb);
			});
			rowHelper.addChild(swatch);
		}

		hexField.setResponder(text -> {
			Integer parsed = KubeUILayoutMath.parseHexColor(text);
			if (parsed != null) {
				handle.color = 0xFF000000 | parsed;
				e.onChange().accept(context, handle.color);
			}
		});

		grid.addChild(hexField, 1, 0, 1, count);
		return grid;
	}

	private void onResizeDrag(double dy) {
		if (resizableLayout == null) {
			return;
		}

		int min = builder.resizableMinHeight != null ? builder.resizableMinHeight : RESIZE_HANDLE_SIZE;
		int max = builder.resizableMaxHeight != null ? builder.resizableMaxHeight : Integer.MAX_VALUE;
		int newHeight = KubeUILayoutMath.clampInt((int) Math.round(resizableLayout.getHeight() + dy), min, max);

		resizableLayout.setMaxHeight(newHeight);
		resizeCurrentHeight = resizableLayout.getHeight();

		if (resizeHandle != null) {
			resizeHandle.setY(root.getY() + root.getHeight() - RESIZE_HANDLE_SIZE);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (builder.draggable && titleBarRect.containsPoint((int) event.x(), (int) event.y())) {
			draggingWindow = true;
			dragStartMouseX = event.x();
			dragStartMouseY = event.y();
			dragStartOffsetX = dragOffsetX;
			dragStartOffsetY = dragOffsetY;
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (draggingWindow) {
			dragOffsetX = dragStartOffsetX + (int) (event.x() - dragStartMouseX);
			dragOffsetY = dragStartOffsetY + (int) (event.y() - dragStartMouseY);

			root.setX(baseX + dragOffsetX);
			root.setY(baseY + dragOffsetY);
			titleBarRect = new ScreenRectangle(new ScreenPosition(root.getX(), root.getY() - TITLE_BAR_HEIGHT), root.getWidth(), TITLE_BAR_HEIGHT);

			if (resizeHandle != null) {
				resizeHandle.setX(root.getX() + root.getWidth() - RESIZE_HANDLE_SIZE);
				resizeHandle.setY(root.getY() + root.getHeight() - RESIZE_HANDLE_SIZE);
			}

			return true;
		}

		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (draggingWindow) {
			draggingWindow = false;
			return true;
		}

		return super.mouseReleased(event);
	}

	private Component styledText(String text) {
		var component = Component.literal(text);
		if (builder.customFont != null) {
			component = component.withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(new FontDescription.Resource(builder.customFont)));
		}
		return component;
	}

	private void playSound(Identifier id) {
		if (id == null) {
			return;
		}
		var sound = SoundEvent.createVariableRangeEvent(id);
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
	}

	private static Component radioLabel(String option, boolean selected) {
		return Component.literal((selected ? "> " : "  ") + option);
	}

	static List<String> selectedOf(CheckboxGroupHandle handle) {
		var result = new ArrayList<String>();
		for (int i = 0; i < handle.boxes.size(); i++) {
			if (handle.boxes.get(i).selected()) {
				result.add(handle.options.get(i));
			}
		}
		return result;
	}

	/// `widthPercent`/`heightPercent` (from `.width("50%")`/`.height("50%")`) take priority over a
	/// fixed pixel `width`/`height` and are resolved against the screen's *current* size, so a
	/// screen built with a percentage stays proportional across different window sizes/resolutions
	/// instead of a fixed pixel count that might be huge on one player's screen and tiny on
	/// another's. Percentages are already screen-relative, so [KubeUITheme#uiScale] (a *personal*
	/// multiplier, not a per-script one) only applies to pixel-based widths/heights - applying it
	/// to a percentage too would make "50%" no longer mean 50% of the screen.
	private int resolveWidth(KubeUIScreenBuilder.Entry entry, int fallback) {
		if (entry.style.widthPercent != null) {
			return Math.round(entry.style.widthPercent * width);
		}
		int px = KubeUILayoutMath.resolveWidth(entry.style.width, fallback);
		return Math.max(1, Math.round(px * KubeUITheme.uiScale));
	}

	private int resolveHeight(KubeUIScreenBuilder.Entry entry, int fallback) {
		if (entry.style.heightPercent != null) {
			return Math.round(entry.style.heightPercent * height);
		}
		int px = KubeUILayoutMath.resolveHeight(entry.style.height, fallback);
		return Math.max(1, Math.round(px * KubeUITheme.uiScale));
	}

	private float animationAlpha() {
		if (!builder.animated) {
			return 1f;
		}

		long now = System.currentTimeMillis();

		if (closing) {
			float t = Math.min(1f, (now - closeStartTime) / (float) builder.animationDurationMs);
			return 1f - t;
		}

		float t = Math.min(1f, (now - openStartTime) / (float) builder.animationDurationMs);
		return t;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);

		if (builder.animated) {
			float alpha = animationAlpha();
			for (var widget : allWidgets) {
				widget.setAlpha(alpha);
			}
		}

		if (builder.draggable && root != null) {
			graphics.centeredText(font, getTitle().getString(), root.getX() + root.getWidth() / 2, root.getY() - TITLE_BAR_HEIGHT + 4, KubeUITheme.titleColor);
		} else {
			graphics.centeredText(font, getTitle().getString(), width / 2, 12, KubeUITheme.titleColor);
		}

		if (KubeUIDebug.outlineEnabled) {
			for (var widget : allWidgets) {
				if (!widget.visible) {
					continue;
				}
				int x0 = widget.getX();
				int y0 = widget.getY();
				int x1 = x0 + widget.getWidth();
				int y1 = y0 + widget.getHeight();
				graphics.fill(x0, y0, x1, y0 + 1, 0xFFFF00FF);
				graphics.fill(x0, y1 - 1, x1, y1, 0xFFFF00FF);
				graphics.fill(x0, y0, x0 + 1, y1, 0xFFFF00FF);
				graphics.fill(x1 - 1, y0, x1, y1, 0xFFFF00FF);
			}
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	static final class RadioGroupHandle {
		final List<String> options;
		final List<Button> buttons = new ArrayList<>();
		String selected;

		RadioGroupHandle(List<String> options, String selected) {
			this.options = options;
			this.selected = selected;
		}
	}

	static final class NumberHandle {
		final int min;
		final int max;
		int value;
		final EditBox field;

		NumberHandle(int min, int max, int value, EditBox field) {
			this.min = min;
			this.max = max;
			this.value = value;
			this.field = field;
		}
	}

	static final class ColorPickerHandle {
		int color;
		final EditBox hexField;

		ColorPickerHandle(EditBox hexField) {
			this.hexField = hexField;
		}
	}

	static final class CheckboxGroupHandle {
		final List<String> options;
		final List<Checkbox> boxes = new ArrayList<>();

		CheckboxGroupHandle(List<String> options) {
			this.options = options;
		}
	}
}
