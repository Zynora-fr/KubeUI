package dev.kubeui.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import dev.kubeui.KubeUI;
import dev.latvian.mods.kubejs.script.ScriptType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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
	private static final int SPINNER_SIZE = 16;
	private static final int RANGE_SLIDER_HEIGHT = 24;
	private static final int SEARCH_DEBOUNCE_MS = 400;
	private static final int CHART_HEIGHT = 60;
	private static final int TITLE_BAR_HEIGHT = 16;
	private static final int NON_DRAGGABLE_TITLE_RESERVE = 24; // room for the fixed title text drawn at y=12 (see extractRenderState)
	private static final int RESIZE_HANDLE_SIZE = 8;
	private static final int MINIMIZE_BUTTON_SIZE = 12;
	// Okabe & Ito's "Color Universal Design" palette (2008) - chosen to stay distinguishable under
	// protanopia/deuteranopia/tritanopia simulation, unlike the arbitrary saturated hues this
	// replaced. White/grey/black bookends kept from the original set (neutrals are colorblind-safe
	// by construction - no hue to confuse).
	private static final int[] COLOR_PALETTE = {
		0xFFFFFF, 0xE69F00, 0x56B4E9, 0x009E73, 0xF0E442, 0x0072B2, 0xD55E00, 0xCC79A7, 0x999999, 0x000000
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
	final Map<String, KubeUIRating> ratings = new HashMap<>();
	final Map<String, KubeUIKeybindCapture> keybindCaptures = new HashMap<>();
	final Map<String, KubeUIRangeSlider> rangeSliders = new HashMap<>();
	final Map<String, KubeUISteppedSlider> steppedSliders = new HashMap<>();
	final Map<String, DatePickerHandle> datePickers = new HashMap<>();
	final Map<AbstractWidget, ContextMenuSpec> contextMenus = new HashMap<>();
	final List<KubeUIScreenBuilder.SearchBoxElement> activeSearchBoxes = new ArrayList<>();
	final Map<String, KubeUIListSelectionState> listSelections = new HashMap<>();
	final List<KubeUIMinimap> minimaps = new ArrayList<>();
	final List<AbsoluteWidgetSpec> absoluteWidgets = new ArrayList<>();
	final Map<String, LayoutElement> byId = new HashMap<>();
	final Map<AbstractWidget, Object> dragSources = new HashMap<>();
	final Map<AbstractWidget, BiConsumer<KubeUIContext, Object>> dropTargets = new HashMap<>();
	final Map<AbstractWidget, Consumer<KubeUIContext>> doubleClickHandlers = new HashMap<>();
	final Map<AbstractWidget, HoverPreviewSpec> hoverPreviews = new HashMap<>();
	final Map<AbstractWidget, Long> hoverStartTimes = new HashMap<>();
	final Map<AbstractWidget, String> permissionGatedWidgets = new HashMap<>();
	private final Map<String, Boolean> permissionCache = new HashMap<>();
	private final Set<String> pendingPermissionGates = new HashSet<>();
	final Map<EditBox, java.util.Deque<String>> undoStacks = new HashMap<>();
	final Map<EditBox, java.util.Deque<String>> redoStacks = new HashMap<>();
	final Map<EditBox, String> lastKnownValues = new HashMap<>();
	final List<AbstractScrollArea> scrollAreas = new ArrayList<>();
	final Map<AbstractScrollArea, Double> scrollVelocity = new HashMap<>();

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
	private KubeUIContextMenuWidget activeContextMenu;
	private boolean minimized;

	private AbstractWidget dragCandidateWidget;
	private double dragCandidateStartX;
	private double dragCandidateStartY;
	private AbstractWidget activeDragWidget;
	private Object activeDragPayload;

	private AbstractWidget activeHoverPreviewWidget;
	private LayoutElement activeHoverPreviewLayout;

	private long shakeStartTime;
	private static final int SHAKE_DURATION_MS = 300;
	private static final int MAX_UNDO_HISTORY = 50;
	private boolean suppressUndoRecording;

	private ScrollableLayout resizableLayout;
	private KubeUIResizeHandle resizeHandle;
	private Integer resizeCurrentHeight;
	private Button minimizeButton;

	private final List<AbstractWidget> allWidgets = new ArrayList<>();
	private long openStartTime;
	private boolean closing;
	private long closeStartTime;

	private KubeUIScreen crossFadeFrom;
	private long crossFadeStartTime;
	private int crossFadeDurationMs = 200;

	public KubeUIScreen(KubeUIScreenBuilder builder) {
		super(builder.title);
		this.builder = builder;
	}

	/// Builds this screen's widget tree without the normal `Screen#init()` lifecycle (which needs
	/// `Minecraft.setScreen(this)` to have actually happened) and returns the resulting widgets -
	/// for injecting a KubeUI-built panel into a *different*, vanilla-owned screen instead of
	/// opening its own (see `KubeUIScreenInjector`). Doesn't fire `KubeUIEvents.SCREEN_OPEN` or
	/// `.onOpen(...)` - this isn't really "opening" a screen from the player's perspective.
	///
	/// `Screen#init(Minecraft, int, int)` - the only place that normally sets `this.minecraft`/
	/// `this.font` - never runs for a detached screen, so both are set here by hand before
	/// `rebuild()` creates any widget. Left unset, `this.font` stays null and every `StringWidget`
	/// `rebuild()` builds bakes in that null reference; added straight onto the host screen via
	/// `KubeUIScreenInjectorHandler`, they then NPE the *host* screen's render the moment vanilla
	/// draws them, regardless of the host's own (perfectly valid) font.
	public List<AbstractWidget> buildDetached(int width, int height) {
		this.width = width;
		this.height = height;
		this.minecraft = Minecraft.getInstance();
		this.font = this.minecraft.font;
		rebuild();
		return allWidgets;
	}

	/// Backs `screen.transitionTo(newBuilder)` - `previous` (the screen this one is replacing,
	/// already open and about to stop being `Minecraft.screen`) keeps rendering itself, fading out,
	/// for `durationMs` while this screen's own content fades in over it, instead of the abrupt
	/// close-then-open `Minecraft.setScreen` normally is. `previous` is otherwise a completely
	/// normal (if about-to-be-discarded) `KubeUIScreen` - drawn via the same detached render path
	/// `KubeUIScreenInjector` uses, just for one screen's worth of widgets instead of injecting them.
	void beginCrossFadeFrom(KubeUIScreen previous, int durationMs) {
		this.crossFadeFrom = previous;
		this.crossFadeStartTime = System.currentTimeMillis();
		this.crossFadeDurationMs = Math.max(1, durationMs);
	}

	private float crossFadeT() {
		if (crossFadeFrom == null) {
			return 1f;
		}
		long elapsed = System.currentTimeMillis() - crossFadeStartTime;
		return Math.min(1f, elapsed / (float) crossFadeDurationMs);
	}

	@Override
	protected void init() {
		super.init();

		if (activeTab == null && !builder.tabs.isEmpty()) {
			activeTab = builder.tabs.get(0).name();
		}

		if (builder.draggable) {
			restorePersistedDragPosition();
		}

		openStartTime = System.currentTimeMillis();
		rebuild();
		KubeUIDebug.trackOpened(this);

		if (builder.screenId != null && Minecraft.getInstance().player != null) {
			KubeUINetworking.sendScreenState(builder.screenId, true);
		}

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
	/// preserving drag offset, resize height, and scroll position. Triggered by tab switches and
	/// [KubeUIContext#update] - never by a plain state change (see class docs). The scroll part
	/// matters a lot for `.reorderableList(...)`, which calls this on every drag step that moves a
	/// row: without restoring it, a rebuild while scrolled down would snap back to the top mid-drag.
	/// Times every [#rebuildInternal] call (backs `/kubeui profile`) - a thin wrapper rather
	/// than instrumenting the body in place, so every existing call site (tab switches,
	/// [KubeUIContext#update], `.init()`, ...) keeps working unchanged.
	void rebuild() {
		long start = System.nanoTime();
		rebuildInternal();
		KubeUIDebug.recordBuild(System.nanoTime() - start, allWidgets.size());
	}

	private void rebuildInternal() {
		double savedScrollAmount = captureScrollAmount(root);

		clearWidgets();
		clearState();

		Layout content;
		if (minimized) {
			// Collapsed to just the title bar: an (almost) empty layout, but not literally
			// zero-width, or the title bar above it would collapse to a sliver too.
			var spacerGrid = new GridLayout();
			spacerGrid.createRowHelper(1).addChild(new KubeUISpacer(builder.elementWidth, 0));
			content = spacerGrid;
		} else {
			content = !builder.tabs.isEmpty() ? buildTabbedContent() : buildContainer(builder, 1);
		}
		Layout positioned = content;

		int marginX = builder.anchorMarginX;
		int marginY = builder.anchorMarginY;
		// A screen anchored to a given edge (not centered on that axis) is pushed inward by
		// whatever's currently reserved there via `KubeUI.reserveSafeArea(...)`.
		if (builder.anchorX <= 0.01f) {
			marginX += KubeUISafeArea.left();
		} else if (builder.anchorX >= 0.99f) {
			marginX += KubeUISafeArea.right();
		}
		if (builder.anchorY <= 0.01f) {
			marginY += KubeUISafeArea.top();
		} else if (builder.anchorY >= 0.99f) {
			marginY += KubeUISafeArea.bottom();
		}
		// The title (drawn separately - see extractRenderState) is either fixed near the top of the
		// screen (non-draggable) or a bar directly above the content (draggable) - either way,
		// content needs to start below it, or a vertically-centered/top-anchored screen ends up with
		// its own text rendered right underneath/through the title.
		int topReserve = builder.draggable ? TITLE_BAR_HEIGHT : NON_DRAGGABLE_TITLE_RESERVE;

		if (!minimized && builder.resizableMaxHeight != null) {
			int initialHeight = resizeCurrentHeight != null ? resizeCurrentHeight : builder.resizableMaxHeight;
			var scrollable = new ScrollableLayout(Minecraft.getInstance(), content, initialHeight);
			trackScrollArea(scrollable);
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
				trackScrollArea(autoScroll);
				autoScroll.setMinWidth(builder.elementWidth);
				positioned = autoScroll;
			}
		}

		positioned.arrangeElements();
		restoreScrollAmount(positioned, savedScrollAmount);
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

		if (builder.minimizable && builder.draggable) {
			int size = MINIMIZE_BUTTON_SIZE;
			minimizeButton = Button.builder(Component.literal(minimized ? "▸" : "▾"), b -> {
				minimized = !minimized;
				rebuild();
			}).bounds(root.getX() + root.getWidth() - size, root.getY() - TITLE_BAR_HEIGHT + (TITLE_BAR_HEIGHT - size) / 2, size, size).build();
			addRenderableWidget(minimizeButton);
		} else {
			minimizeButton = null;
		}

		// Positioned relative to the content area (not the raw screen), and added after every
		// normal-flow widget so they naturally paint on top - sorted by zIndex among themselves,
		// for elements that intentionally overlap each other too (a badge on another absolute icon).
		// zIndex ties (the common case - most scripts never bother setting one) break by visual
		// position (top-to-bottom, then left-to-right) instead of declaration order: since add
		// order here also becomes Tab-focus tie-break order (vanilla's own stable sort on
		// `tabOrderGroup`, which every widget here defaults to unless `.tabOrder(...)` said
		// otherwise), an arbitrary declaration-order default would tab-navigate in a way that has
		// nothing to do with what's actually on screen.
		absoluteWidgets.sort(
			Comparator.comparingInt(AbsoluteWidgetSpec::zIndex)
				.thenComparingInt(AbsoluteWidgetSpec::y)
				.thenComparingInt(AbsoluteWidgetSpec::x)
		);
		for (var spec : absoluteWidgets) {
			spec.widget().setX(root.getX() + spec.x());
			spec.widget().setY(root.getY() + spec.y());
			addRenderableWidget(spec.widget());
		}

		allWidgets.clear();
		root.visitWidgets(allWidgets::add);
		if (resizeHandle != null) {
			allWidgets.add(resizeHandle);
		}
		if (minimizeButton != null) {
			allWidgets.add(minimizeButton);
		}
		allWidgets.addAll(absoluteWidgets.stream().map(AbsoluteWidgetSpec::widget).toList());

		applyKnownPermissions();
		requestMissingPermissions();
	}

	/// `.requirePermission(gate)` widgets start disabled (see [#applyCommonStyle]) - this
	/// re-enables whichever ones this screen already has a cached answer for, so a `rebuild()`
	/// after the first check response doesn't have to wait on the network again.
	private void applyKnownPermissions() {
		permissionGatedWidgets.forEach((widget, gate) -> {
			Boolean known = permissionCache.get(gate);
			if (known != null) {
				widget.active = known;
			}
		});
	}

	/// Sends one batched check (see [KubeUIActions#PERMISSION_CHECK_ACTION]) for every distinct
	/// gate on this screen that isn't already cached or already in flight - never one request per
	/// widget, and never a repeat request for a gate this screen already asked about.
	private void requestMissingPermissions() {
		if (permissionGatedWidgets.isEmpty() || Minecraft.getInstance().player == null) {
			return;
		}

		var toRequest = new HashSet<String>();
		for (String gate : permissionGatedWidgets.values()) {
			if (!permissionCache.containsKey(gate) && pendingPermissionGates.add(gate)) {
				toRequest.add(gate);
			}
		}
		if (toRequest.isEmpty()) {
			return;
		}

		var gates = new ListTag();
		for (String gate : toRequest) {
			gates.add(StringTag.valueOf(gate));
		}
		var data = new CompoundTag();
		data.put("gates", gates);
		KubeUINetworking.sendAction(KubeUIActions.PERMISSION_CHECK_ACTION, data);
	}

	/// Called (via [KubeUINetworking]) once the server replies to a [#requestMissingPermissions]
	/// batch - `results` maps each requested gate name to whether this player has it.
	void applyPermissionResults(CompoundTag results) {
		for (String gate : results.getAllKeys()) {
			permissionCache.put(gate, KubeUINbtCompat.getBooleanOr(results, gate, false));
			pendingPermissionGates.remove(gate);
		}
		applyKnownPermissions();
	}

	/// Dispatches a permission-check response (see [#applyPermissionResults]) to whichever
	/// currently-displayed screen(s) it's for - a plain screen, or every window of a
	/// [KubeUIMultiWindowHost] if `.nonModal()` ones are open. A screen not (or no longer) among
	/// them just never gets its pending gates resolved - harmless, its widgets simply stay
	/// disabled, the same as if the response had been lost.
	static void receivePermissionResults(CompoundTag results) {
		var screen = Minecraft.getInstance().screen;
		if (screen instanceof KubeUIScreen kubeUIScreen) {
			kubeUIScreen.applyPermissionResults(results);
		} else if (screen instanceof KubeUIMultiWindowHost host) {
			for (var window : host.windows()) {
				window.applyPermissionResults(results);
			}
		}
	}

	/// 0 if `layout` isn't scrollable (or is null, e.g. the very first build). `ScrollableLayout`
	/// doesn't expose scroll position itself - it delegates to a private inner widget that's only
	/// reachable, from outside, through the public [AbstractScrollArea] it happens to extend.
	private double captureScrollAmount(Layout layout) {
		if (!(layout instanceof ScrollableLayout scrollable)) {
			return 0;
		}

		double[] amount = {0};
		scrollable.visitChildren(el -> {
			if (el instanceof AbstractScrollArea area) {
				amount[0] = area.scrollAmount();
			}
		});
		return amount[0];
	}

	private void restoreScrollAmount(Layout layout, double amount) {
		if (amount <= 0 || !(layout instanceof ScrollableLayout scrollable)) {
			return;
		}

		scrollable.visitChildren(el -> {
			if (el instanceof AbstractScrollArea area) {
				area.setScrollAmount(amount);
			}
		});
	}

	private static final double SCROLL_MOMENTUM_KICK = 10.0;
	private static final double SCROLL_MOMENTUM_DECAY = 0.85;
	private static final double SCROLL_MOMENTUM_STOP_THRESHOLD = 0.05;

	/// Every `.scrollPanel()`/root-resizable/auto-scroll/`.splitPane()` pane's real scroll area,
	/// tracked so `#mouseScrolled` can add a bit of momentum on top of the normal (already-applied
	/// by then) vanilla scroll - see `AbstractScrollArea` is reachable through `ScrollableLayout`
	/// only via `#visitChildren` (its actual container widget is a private nested class).
	private void trackScrollArea(ScrollableLayout scrollable) {
		scrollable.visitChildren(el -> {
			if (el instanceof AbstractScrollArea area) {
				scrollAreas.add(area);
			}
		});
	}

	/// Decays every scroll area's momentum (if any) by one tick, applying the (shrinking) leftover
	/// velocity - `AbstractScrollArea#setScrollAmount` already clamps to `[0, max]`, so this can't
	/// overshoot past either end.
	private void tickScrollMomentum() {
		var it = scrollVelocity.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			double velocity = entry.getValue();
			if (Math.abs(velocity) < SCROLL_MOMENTUM_STOP_THRESHOLD) {
				it.remove();
				continue;
			}
			var area = entry.getKey();
			area.setScrollAmount(area.scrollAmount() - velocity);
			entry.setValue(velocity * SCROLL_MOMENTUM_DECAY);
		}
	}

	private void clearState() {
		dismissContextMenu();
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
		ratings.clear();
		keybindCaptures.clear();
		rangeSliders.clear();
		steppedSliders.clear();
		datePickers.clear();
		contextMenus.clear();
		activeSearchBoxes.clear();
		listSelections.clear();
		minimaps.clear();
		absoluteWidgets.clear();
		dragSources.clear();
		dropTargets.clear();
		doubleClickHandlers.clear();
		hoverPreviews.clear();
		hoverStartTimes.clear();
		permissionGatedWidgets.clear();
		undoStacks.clear();
		redoStacks.clear();
		lastKnownValues.clear();
		scrollAreas.clear();
		scrollVelocity.clear();
		dragCandidateWidget = null;
		activeDragWidget = null;
		activeDragPayload = null;
		dismissHoverPreview();
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

		long now = System.currentTimeMillis();
		for (var searchBox : activeSearchBoxes) {
			if (searchBox.pending && now - searchBox.lastKeystrokeAt >= SEARCH_DEBOUNCE_MS) {
				searchBox.pending = false;
				if (searchBox.onQueryChange != null) {
					searchBox.onQueryChange.accept(context, searchBox.query);
				}
			}
		}

		if (closing && System.currentTimeMillis() - closeStartTime >= builder.animationDurationMs) {
			closeSelf();
		}

		for (var map : minimaps) {
			map.tick();
		}

		tickScrollMomentum();
		tickHoverPreviews(now);

		if (builder.onTickCallback != null) {
			builder.onTickCallback.accept(context);
		}
	}

	@Override
	public void removed() {
		super.removed();

		if (builder.persistKey != null) {
			savePersistedState();
		}

		if (builder.screenId != null && Minecraft.getInstance().player != null) {
			KubeUINetworking.sendScreenState(builder.screenId, false);
		}

		if (builder.onCloseCallback != null) {
			builder.onCloseCallback.accept(context);
		}

		KubeUIEvents.SCREEN_CLOSE.post(ScriptType.CLIENT, new KubeUIScreenEvent(context, builder.title.getString()));
	}

	/// `.nonModal()` windows share one real `Minecraft.screen` (a [KubeUIMultiWindowHost]) - closing
	/// one must only remove *that* window from the host, not null out `Minecraft.screen` and take
	/// every other non-modal window down with it. `host` is null for a normal, non-`.nonModal()`
	/// screen, where this is exactly the old `setScreen(null)` behavior.
	private KubeUIMultiWindowHost host;

	void setHost(KubeUIMultiWindowHost host) {
		this.host = host;
	}

	private void closeSelf() {
		if (host != null) {
			host.removeWindow(this);
		} else {
			Minecraft.getInstance().setScreen(null);
		}
	}

	/// Starts closing the screen - immediately if `.animated()` wasn't requested, or after a fade
	/// out otherwise. Used by both `screen.close()` and Esc (see [#onClose]).
	void requestClose() {
		if (!builder.animated) {
			closeSelf();
		} else if (!closing) {
			closing = true;
			closeStartTime = System.currentTimeMillis();
		}
	}

	/// `screen.shake()` - a brief shake + red flash, for signaling a refused action without a
	/// blocking `.alert()`. Purely visual, drawn via the same transform used for `.renderScale(...)`/
	/// slide-scale transitions (see [#extractRenderState]) - doesn't touch any widget's real position.
	void shake() {
		shakeStartTime = System.currentTimeMillis();
	}

	/// `.textField(...).undoable()`, Ctrl+Z. `suppressUndoRecording` stops `setValue` (called here)
	/// from re-triggering the responder's own undo-stack push - otherwise undoing would immediately
	/// push the just-undone value right back, making it a no-op.
	private void undo(EditBox editBox) {
		var undo = undoStacks.get(editBox);
		if (undo == null || undo.isEmpty()) {
			return;
		}
		redoStacks.get(editBox).push(editBox.getValue());
		String previous = undo.pop();
		suppressUndoRecording = true;
		editBox.setValue(previous);
		suppressUndoRecording = false;
		lastKnownValues.put(editBox, previous);
	}

	/// Same as [#undo(EditBox)], Ctrl+Y (or Ctrl+Shift+Z).
	private void redo(EditBox editBox) {
		var redo = redoStacks.get(editBox);
		if (redo == null || redo.isEmpty()) {
			return;
		}
		undoStacks.get(editBox).push(editBox.getValue());
		String next = redo.pop();
		suppressUndoRecording = true;
		editBox.setValue(next);
		suppressUndoRecording = false;
		lastKnownValues.put(editBox, next);
	}

	@Override
	public boolean keyPressed(int keyCode, int scancode, int modifiers) {
		return keyPressed(new KeyEvent(keyCode, scancode, modifiers), keyCode, scancode, modifiers);
	}

	private boolean keyPressed(KeyEvent event, int keyCode, int scancode, int modifiers) {
		if (getFocused() instanceof EditBox editBox && undoStacks.containsKey(editBox) && event.hasControlDown()) {
			if (event.key() == GLFW.GLFW_KEY_Z) {
				if (event.hasShiftDown()) {
					redo(editBox);
				} else {
					undo(editBox);
				}
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_Y) {
				redo(editBox);
				return true;
			}
		}

		// Hotkeys don't fire while a text field has focus - a hotkey bound to a plain letter would
		// otherwise make that letter untypable in any focused field.
		boolean typing = getFocused() instanceof EditBox || getFocused() instanceof MultiLineEditBox;
		if (!typing) {
			var hotkey = builder.hotkeys.get(event.key());
			if (hotkey != null) {
				hotkey.accept(context);
				return true;
			}
		}

		return super.keyPressed(keyCode, scancode, modifiers);
	}

	/// `ContainerEventHandler#handleTabNavigation` (the real vanilla Tab-navigation logic, private -
	/// can't be hooked directly) doesn't wrap around: Tab past the last widget just stops rather
	/// than cycling back to the first. Harmless for most screens, but not a real focus *trap* for
	/// `KubeUI.confirm()`/`.alert()` (full screens with just 1-2 buttons) - clearing focus and
	/// retrying once, when the normal path comes back empty, makes it wrap both ways instead.
	@Override
	public net.minecraft.client.gui.ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
		var path = super.nextFocusPath(event);
		if (path != null || !(event instanceof net.minecraft.client.gui.navigation.FocusNavigationEvent.TabNavigation)) {
			return path;
		}
		setFocused((net.minecraft.client.gui.components.events.GuiEventListener) null);
		return super.nextFocusPath(event);
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

		if (builder.draggable) {
			// Reserved keys (a script's own widget ids can't start with "__" in practice without
			// asking for trouble) - drag offset isn't tied to any one widget, unlike everything else here.
			state.put("__dragOffsetX", dragOffsetX);
			state.put("__dragOffsetY", dragOffsetY);
			// Also written to disk (KubeUIScreenBuilder.PERSISTED above is in-memory only, cleared
			// on JVM restart) - real cross-launch persistence, see KubeUIWindowPositions.
			KubeUIWindowPositions.set(builder.persistKey, dragOffsetX, dragOffsetY);
		}

		KubeUIScreenBuilder.PERSISTED.put(builder.persistKey, state);
	}

	/// Extension of the same `persistKey` mechanism [#savePersistedState] already uses for widget
	/// values, to a `.draggable()` window's last dragged-to position - restored once, before the
	/// first `rebuild()`, so it survives closing and reopening a screen that shares a `persistKey`.
	/// Prefers the in-memory value (same session, cheap) and falls back to the on-disk one (see
	/// [#savePersistedState]) so the position also survives a full game restart, not just a
	/// close/reopen within the same session.
	private void restorePersistedDragPosition() {
		if (persisted("__dragOffsetX") instanceof Integer x) {
			dragOffsetX = x;
		} else if (KubeUIWindowPositions.get(builder.persistKey) instanceof int[] saved) {
			dragOffsetX = saved[0];
			dragOffsetY = saved[1];
			return;
		}
		if (persisted("__dragOffsetY") instanceof Integer y) {
			dragOffsetY = y;
		}
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
		return buildContainer(owner, columns, null);
	}

	/// Same as [#buildContainer(KubeUIScreenBuilder, int)], but for a `.row(...)` whose own entry
	/// has an explicit width (`rowTargetWidth`, resolved by the caller) - only then is there real
	/// "leftover space" for `.grow(weight)` children to expand into. `.absolute(x, y)` entries are
	/// pulled out of the grid entirely (see [#absoluteWidgets]) rather than occupying a cell.
	private Layout buildContainer(KubeUIScreenBuilder owner, int columns, Integer rowTargetWidth) {
		var grid = new GridLayout().spacing(SPACING);
		var rowHelper = grid.createRowHelper(Math.max(1, columns));
		var growCandidates = new ArrayList<AbstractWidget>();
		var growWeights = new ArrayList<Integer>();
		int naturalTotal = 0;

		for (var entry : owner.entries) {
			if (entry.style.absoluteX != null) {
				LayoutElement absolute = buildElement(owner, entry);
				applyCommonStyle(absolute, entry.style);
				if (absolute instanceof AbstractWidget widget) {
					absoluteWidgets.add(new AbsoluteWidgetSpec(widget, entry.style.absoluteX, entry.style.absoluteY, entry.style.zIndex != null ? entry.style.zIndex : 0));
				}
				continue;
			}

			LayoutElement child = buildElement(owner, entry);
			applyCommonStyle(child, entry.style);

			String id = entry.effectiveId();
			if (id != null) {
				byId.put(id, child);
			}

			naturalTotal += child.getWidth() + SPACING;

			if (rowTargetWidth != null && entry.style.growWeight != null && entry.style.growWeight > 0 && child instanceof AbstractWidget widget) {
				growCandidates.add(widget);
				growWeights.add(entry.style.growWeight);
			}

			var settings = rowHelper.newCellSettings()
				.align(entry.style.alignX, entry.style.alignY)
				.padding(entry.style.paddingLeft, entry.style.paddingTop, entry.style.paddingRight, entry.style.paddingBottom);
			rowHelper.addChild(child, settings);
		}

		if (rowTargetWidth != null && !growCandidates.isEmpty()) {
			int leftover = rowTargetWidth - naturalTotal;
			if (leftover > 0) {
				int totalWeight = growWeights.stream().mapToInt(Integer::intValue).sum();
				for (int i = 0; i < growCandidates.size(); i++) {
					int extra = Math.round(leftover * (growWeights.get(i) / (float) totalWeight));
					var widget = growCandidates.get(i);
					widget.setWidth(widget.getWidth() + extra);
				}
			}
		}

		return grid;
	}

	record AbsoluteWidgetSpec(AbstractWidget widget, int x, int y, int zIndex) {
	}

	private void applyCommonStyle(LayoutElement element, KubeUIScreenBuilder.Style style) {
		if (style.richTooltipLines != null && !style.richTooltipLines.isEmpty()) {
			var joined = Component.empty();
			for (int i = 0; i < style.richTooltipLines.size(); i++) {
				if (i > 0) {
					joined.append(Component.literal("\n"));
				}
				joined.append(style.richTooltipLines.get(i));
			}
			element.visitWidgets(w -> w.setTooltip(Tooltip.create(joined)));
		} else if (style.tooltip != null) {
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

		if ((style.contextMenuItems != null && !style.contextMenuItems.isEmpty()) || style.contextMenuItemsSupplier != null) {
			var spec = new ContextMenuSpec(style.contextMenuItems, style.contextMenuItemsSupplier, style.contextMenuOnSelect);
			element.visitWidgets(w -> {
				if (w instanceof AbstractWidget widget) {
					contextMenus.put(widget, spec);
				}
			});
		}

		if (style.dragPayload != null) {
			element.visitWidgets(w -> dragSources.put(w, style.dragPayload));
		}

		if (style.dropHandler != null) {
			element.visitWidgets(w -> dropTargets.put(w, style.dropHandler));
		}

		if (style.onDoubleClick != null) {
			element.visitWidgets(w -> doubleClickHandlers.put(w, style.onDoubleClick));
		}

		if (style.hoverPreviewDelayMs != null && style.hoverPreviewBuilder != null) {
			element.visitWidgets(w -> hoverPreviews.put(w, new HoverPreviewSpec(style.hoverPreviewDelayMs, style.hoverPreviewBuilder)));
		}

		if (style.requiredPermission != null) {
			element.visitWidgets(w -> {
				if (w instanceof AbstractWidget widget) {
					permissionGatedWidgets.put(widget, style.requiredPermission);
					widget.active = false;
				}
			});
		}
	}

	/// `items` is null when built from the dynamic overload (`itemsSupplier` non-null instead) -
	/// resolved fresh every time the menu opens (see `#openContextMenu`), never both at once.
	record ContextMenuSpec(List<String> items, java.util.function.Function<KubeUIContext, List<String>> itemsSupplier, BiConsumer<KubeUIContext, String> onSelect) {
	}

	record HoverPreviewSpec(int delayMs, Consumer<KubeUIScreenBuilder> builder) {
	}

	private static final int DRAG_HANDLE_WIDTH = 20;

	private LayoutElement buildReorderableList(KubeUIScreenBuilder owner, KubeUIScreenBuilder.ReorderableListElement e) {
		var nested = KubeUIScreenBuilder.freshChildBuilder(owner);

		for (int displayPos = 0; displayPos < e.order.size(); displayPos++) {
			int itemIndex = e.order.get(displayPos);
			Object item = e.items.get(itemIndex);
			int pos = displayPos;

			nested.row(row -> {
				row.reorderHandle(pos, e.dragState, e).width(DRAG_HANDLE_WIDTH);
				try {
					e.renderer.render(row, item, itemIndex);
				} catch (Exception ex) {
					KubeUI.LOGGER.error("KubeUI: error rendering reorderableList('{}', ...) item at index {}", e.id, itemIndex, ex);
				}
			});
		}

		return buildContainer(nested, 1);
	}

	private static final int SPLIT_DIVIDER_WIDTH = 4;

	private LayoutElement buildSplitPane(KubeUIScreenBuilder.SplitPaneElement e) {
		int leftWidth = (int) Math.round((e.width - SPLIT_DIVIDER_WIDTH) * e.ratio);
		int rightWidth = Math.max(1, e.width - SPLIT_DIVIDER_WIDTH - leftWidth);

		var grid = new GridLayout();
		var rowHelper = grid.createRowHelper(3);

		var leftContent = buildContainer(e.first, 1);
		var leftScroll = new ScrollableLayout(Minecraft.getInstance(), leftContent, e.height);
		trackScrollArea(leftScroll);
		leftScroll.setMinWidth(leftWidth);
		rowHelper.addChild(leftScroll);

		var divider = new KubeUISplitPaneDivider(0, 0, SPLIT_DIVIDER_WIDTH, e.height, dx -> {
			e.ratio = Math.max(0.1, Math.min(0.9, e.ratio + dx / e.width));
			rebuild();
		});
		rowHelper.addChild(divider);

		var rightContent = buildContainer(e.second, 1);
		var rightScroll = new ScrollableLayout(Minecraft.getInstance(), rightContent, e.height);
		trackScrollArea(rightScroll);
		rightScroll.setMinWidth(rightWidth);
		rowHelper.addChild(rightScroll);

		return grid;
	}

	private static final int TREE_INDENT = 12;
	private static final int TREE_TOGGLE_WIDTH = 14;

	private LayoutElement buildTree(KubeUIScreenBuilder owner, KubeUIScreenBuilder.TreeElement e) {
		var nested = KubeUIScreenBuilder.freshChildBuilder(owner);
		appendTreeNodes(nested, e, e.rootNodes, 0);
		return buildContainer(nested, 1);
	}

	private void appendTreeNodes(KubeUIScreenBuilder target, KubeUIScreenBuilder.TreeElement e, List<?> nodes, int depth) {
		for (int i = 0; i < nodes.size(); i++) {
			Object node = nodes.get(i);
			int index = i;
			List<?> children;

			try {
				children = e.childrenOf.children(node);
			} catch (Exception ex) {
				KubeUI.LOGGER.error("KubeUI: error in tree('{}', ...) childrenOf for a node", e.id, ex);
				children = List.of();
			}

			boolean hasChildren = children != null && !children.isEmpty();
			boolean isExpanded = e.expanded.getOrDefault(node, false);

			target.row(row -> {
				row.spacer(0).width(depth * TREE_INDENT);

				if (hasChildren) {
					row.button(isExpanded ? "▼" : "▶", ctx -> {
						e.expanded.put(node, !isExpanded);
						ctx.update(b -> {
						});
					}).width(TREE_TOGGLE_WIDTH);
				} else {
					row.spacer(0).width(TREE_TOGGLE_WIDTH);
				}

				try {
					e.renderer.render(row, node, index);
				} catch (Exception ex) {
					KubeUI.LOGGER.error("KubeUI: error rendering tree('{}', ...) node at depth {}", e.id, depth, ex);
				}
			});

			if (hasChildren && isExpanded) {
				appendTreeNodes(target, e, children, depth + 1);
			}
		}
	}

	private LayoutElement buildElement(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry) {
		return switch (entry.element) {
			case KubeUIScreenBuilder.RowElement e -> {
				boolean hasExplicitWidth = entry.style.width != null || entry.style.widthPercent != null || entry.style.responsiveWidth != null;
				Integer rowTargetWidth = hasExplicitWidth ? resolveWidth(entry, owner.elementWidth) : null;
				yield buildContainer(e.content(), e.content().entries.size() + 1, rowTargetWidth);
			}
			case KubeUIScreenBuilder.GridElement e -> buildContainer(e.content(), e.columns());
			case KubeUIScreenBuilder.ScrollPanelElement e -> {
				var content = buildContainer(e.content(), 1);
				var scrollable = new ScrollableLayout(Minecraft.getInstance(), content, e.maxHeight());
			trackScrollArea(scrollable);
				scrollable.setMinWidth(e.content().elementWidth);
				yield scrollable;
			}
			case KubeUIScreenBuilder.ButtonElement e -> {
				int w = resolveWidth(entry, owner.elementWidth);
				int h = resolveHeight(entry, owner.buttonHeight);
				ResourceLocation sound = entry.style.clickSound;
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
				ResourceLocation sound = entry.style.clickSound;
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

				boolean undoable = entry.style.undoable;
				if (undoable) {
					undoStacks.put(widget, new java.util.ArrayDeque<>());
					redoStacks.put(widget, new java.util.ArrayDeque<>());
				}
				lastKnownValues.put(widget, widget.getValue());

				widget.setResponder(value -> {
					if (undoable && !suppressUndoRecording) {
						var undo = undoStacks.get(widget);
						undo.push(lastKnownValues.get(widget));
						if (undo.size() > MAX_UNDO_HISTORY) {
							undo.removeLast();
						}
						redoStacks.get(widget).clear();
					}
					lastKnownValues.put(widget, value);
					e.onChange().accept(context, value);
				});
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
				var widget = CycleButton.<String>builder(Component::literal)
					.withInitialValue(initial)
					.withValues(e.options())
					.displayOnlyValue()
					.create(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, owner.buttonHeight), Component.literal(e.id()), (button, value) -> e.onChange().accept(context, value));
				dropdowns.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.TextAreaElement e -> {
				var widget = new MultiLineEditBox(font, 0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, e.height()),
					e.hint() != null ? Component.literal(e.hint()) : Component.empty(), Component.literal(e.id()));
				Object saved = persisted(e.id());
				widget.setValue(saved instanceof String s ? s : e.initialValue());
				widget.setValueListener(value -> e.onChange().accept(context, value));
				textAreas.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.ImageElement e ->
				new KubeUIImageWidget(0, 0, resolveWidth(entry, e.width()), resolveHeight(entry, e.height()), e.texture());
			case KubeUIScreenBuilder.ItemElement e -> {
				ResourceLocation sound = entry.style.clickSound;
				var onClick = e.onClick();
				yield new KubeUIItemWidget(0, 0, e.stack(), font, onClick == null ? null : ev -> {
					onClick.accept(context);
					playSound(sound);
				});
			}
			case KubeUIScreenBuilder.RecipeSlotElement e -> {
				ResourceLocation sound = entry.style.clickSound;
				var onClick = e.onClick();
				var stacks = new java.util.ArrayList<net.minecraft.world.item.ItemStack>();
				for (String itemId : e.itemIds()) {
					var itemIdentifier = ResourceLocation.tryParse(itemId);
					var item = itemIdentifier != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemIdentifier).orElse(null) : null;
					if (item != null) {
						stacks.add(new net.minecraft.world.item.ItemStack(item));
					}
				}
				yield new KubeUIRecipeSlotWidget(0, 0, stacks, font, onClick == null ? null : ev -> {
					onClick.accept(context);
					playSound(sound);
				});
			}
			case KubeUIScreenBuilder.ProgressBarElement e -> {
				var widget = new KubeUIProgressBar(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, PROGRESS_HEIGHT), e.value(), e.max(), font, entry.style.styleAccent);
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
			case KubeUIScreenBuilder.RichTextElement e -> {
				int width = resolveWidth(entry, owner.elementWidth);
				int height = KubeUIRichText.wrappedHeight(font, e.text(), width);
				ResourceLocation sound = entry.style.clickSound;
				var onClick = e.onClick();
				yield new KubeUIRichText(0, 0, width, height, e.text(), font, onClick == null ? null : ev -> {
					onClick.accept(context);
					playSound(sound);
				}, entry.style.styleColor);
			}
			case KubeUIScreenBuilder.BadgeElement e -> new KubeUIBadge(0, 0, e.text(), e.color(), font);
			case KubeUIScreenBuilder.RatingElement e -> {
				var widget = new KubeUIRating(0, 0, e.max(), e.initial(), font, context, e.onChange());
				ratings.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.SpinnerElement ignored -> new KubeUISpinner(0, 0, resolveHeight(entry, SPINNER_SIZE));
			case KubeUIScreenBuilder.PanelBackgroundElement e ->
				new KubeUIPanelBackground(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, owner.elementWidth), e.texture());
			case KubeUIScreenBuilder.EntityPreviewElement e -> buildEntityPreview(entry, owner, e);
			case KubeUIScreenBuilder.KeybindCaptureElement e -> {
				var widget = new KubeUIKeybindCapture(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, FIELD_HEIGHT), e.initial(), font, context, e.onChange(), entry.style.styleColor);
				keybindCaptures.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.AccordionElement e -> buildAccordion(owner, entry, e);
			case KubeUIScreenBuilder.BreadcrumbElement e -> buildBreadcrumb(owner, entry, e);
			case KubeUIScreenBuilder.WizardElement e -> buildWizard(owner, entry, e);
			case KubeUIScreenBuilder.RangeSliderElement e -> {
				var widget = new KubeUIRangeSlider(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, RANGE_SLIDER_HEIGHT), e.min(), e.max(), e.initialLow(), e.initialHigh(), font, context, e.onChange(), entry.style.styleColor);
				rangeSliders.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.SteppedSliderElement e -> {
				var widget = new KubeUISteppedSlider(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, owner.buttonHeight), e.steps(), e.initial(), context, e.onChange());
				steppedSliders.put(e.id(), widget);
				yield widget;
			}
			case KubeUIScreenBuilder.DatePickerElement e -> buildDatePicker(owner, entry, e);
			case KubeUIScreenBuilder.SearchableDropdownElement e -> buildSearchableDropdown(owner, entry, e);
			case KubeUIScreenBuilder.MultiSelectDropdownElement e -> buildMultiSelectDropdown(owner, entry, e);
			case KubeUIScreenBuilder.SearchBoxElement e -> buildSearchBox(owner, entry, e);
			case KubeUIScreenBuilder.ResourcePickerElement e -> buildResourcePicker(owner, entry, e);
			case KubeUIScreenBuilder.TableElement e ->
				new KubeUITable(0, 0, resolveWidth(entry, owner.elementWidth), e.columnLabels(), e.columnWidths(), e.rows(), font, context, e.onSort(), entry.style.styleColor, entry.style.styleAccent);
			case KubeUIScreenBuilder.ReorderableListElement e -> buildReorderableList(owner, e);
			case KubeUIScreenBuilder.ReorderHandleElement e -> new KubeUIListDragHandle(0, 0, e.pos(), e.dragState(), e.listElement(), context);
			case KubeUIScreenBuilder.ListSelectCheckboxElement e -> {
				listSelections.putIfAbsent(e.listId(), e.state());
				yield new KubeUIListSelectCheckbox(0, 0, e.index(), e.state(), context, e.onSelectionChange());
			}
			case KubeUIScreenBuilder.ChartElement e ->
				new KubeUIChart(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, CHART_HEIGHT), e.kind(), e.values(), e.labels(), font, entry.style.styleColor, entry.style.styleAccent);
			case KubeUIScreenBuilder.MapElement e -> {
				var widget = new KubeUIMinimap(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, owner.elementWidth), e.radius(), font);
				minimaps.add(widget);
				yield widget;
			}
			case KubeUIScreenBuilder.TreeElement e -> buildTree(owner, e);
			case KubeUIScreenBuilder.SplitPaneElement e -> buildSplitPane(e);
		};
	}

	private LayoutElement buildAccordion(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.AccordionElement e) {
		var grid = new GridLayout().rowSpacing(SPACING);
		String arrow = e.expanded ? "▼ " : "▶ ";

		var header = Button.builder(styledText(arrow + e.title), b -> {
			e.expanded = !e.expanded;
			rebuild();
		}).bounds(0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, owner.buttonHeight)).build();
		grid.addChild(header, 0, 0);

		if (e.expanded) {
			grid.addChild(buildContainer(e.content, 1), 1, 0);
		}

		return grid;
	}

	private LayoutElement buildBreadcrumb(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.BreadcrumbElement e) {
		var grid = new GridLayout().columnSpacing(4);
		var rowHelper = grid.createRowHelper(e.steps().size() * 2);
		int height = resolveHeight(entry, LABEL_HEIGHT);

		for (int i = 0; i < e.steps().size(); i++) {
			String step = e.steps().get(i);
			boolean isLast = i == e.steps().size() - 1;

			LayoutElement segment;
			if (isLast || e.onSelect() == null) {
				segment = new StringWidget(0, 0, font.width(step), height, styledText(step), font);
			} else {
				int index = i;
				segment = Button.builder(styledText(step), b -> e.onSelect().accept(context, index))
					.bounds(0, 0, font.width(step) + 10, height)
					.build();
			}
			rowHelper.addChild(segment);

			if (!isLast) {
				rowHelper.addChild(new StringWidget(0, 0, font.width(">"), height, styledText(">"), font));
			}
		}

		return grid;
	}

	private LayoutElement buildWizard(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.WizardElement e) {
		var grid = new GridLayout().rowSpacing(SPACING);

		String stepLabel = "Step " + (e.currentStep + 1) + "/" + e.stepNames.size() + ": " + e.stepNames.get(e.currentStep);
		grid.addChild(new StringWidget(0, 0, resolveWidth(entry, owner.elementWidth), LABEL_HEIGHT, styledText(stepLabel), font), 0, 0);

		grid.addChild(buildContainer(e.stepContents.get(e.currentStep), 1), 1, 0);

		var nav = new GridLayout().columnSpacing(SPACING);
		int buttonHeight = owner.buttonHeight;

		var prev = Button.builder(styledText("< Previous"), b -> {
			e.currentStep = Math.max(0, e.currentStep - 1);
			rebuild();
		}).bounds(0, 0, 90, buttonHeight).build();
		prev.active = e.currentStep > 0;
		nav.addChild(prev, 0, 0);

		boolean canAdvance = e.canAdvance == null || e.canAdvance.test(e.currentStep);
		var next = Button.builder(styledText(e.currentStep == e.stepNames.size() - 1 ? "Finish" : "Next >"), b -> {
			if (e.currentStep < e.stepNames.size() - 1) {
				e.currentStep++;
				rebuild();
			}
		}).bounds(0, 0, 90, buttonHeight).build();
		next.active = canAdvance && e.currentStep < e.stepNames.size() - 1;
		nav.addChild(next, 0, 1);

		grid.addChild(nav, 2, 0);
		return grid;
	}

	private LayoutElement buildDatePicker(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.DatePickerElement e) {
		var handle = new DatePickerHandle(e.initialYear(), e.initialMonth(), e.initialDay());
		datePickers.put(e.id(), handle);

		int fieldWidth = Math.max(30, resolveWidth(entry, owner.elementWidth) / 3 - SPACING);
		int height = resolveHeight(entry, FIELD_HEIGHT);

		var grid = new GridLayout().columnSpacing(SPACING);
		handle.yearField = buildDateField(String.valueOf(handle.year), fieldWidth, height, 1, 9999, v -> {
			handle.year = v;
			fireDateChange(e, handle);
		});
		handle.monthField = buildDateField(String.valueOf(handle.month), fieldWidth, height, 1, 12, v -> {
			handle.month = v;
			fireDateChange(e, handle);
		});
		handle.dayField = buildDateField(String.valueOf(handle.day), fieldWidth, height, 1, 31, v -> {
			handle.day = v;
			fireDateChange(e, handle);
		});

		grid.addChild(handle.yearField, 0, 0);
		grid.addChild(handle.monthField, 0, 1);
		grid.addChild(handle.dayField, 0, 2);
		return grid;
	}

	private EditBox buildDateField(String initial, int width, int height, int min, int max, java.util.function.IntConsumer onChange) {
		var field = new EditBox(font, 0, 0, width, height, Component.literal("Date field"));
		field.setValue(initial);
		field.setResponder(text -> {
			Integer parsed = KubeUILayoutMath.parseInt(text);
			if (parsed != null) {
				onChange.accept(KubeUILayoutMath.clampInt(parsed, min, max));
			}
		});
		return field;
	}

	private void fireDateChange(KubeUIScreenBuilder.DatePickerElement e, DatePickerHandle handle) {
		if (e.onChange() != null) {
			e.onChange().accept(context, String.format("%04d-%02d-%02d", handle.year, handle.month, handle.day));
		}
	}

	private LayoutElement buildSearchableDropdown(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.SearchableDropdownElement e) {
		int width = resolveWidth(entry, owner.elementWidth);
		var grid = new GridLayout().rowSpacing(2);

		var field = new EditBox(font, 0, 0, width, FIELD_HEIGHT, Component.literal(e.id));
		field.setValue(e.query);
		field.setHint(Component.literal(e.selected != null ? e.selected : "Search..."));
		field.setResponder(text -> {
			e.query = text;
			rebuild();
		});
		grid.addChild(field, 0, 0);

		if (!e.query.isEmpty()) {
			String needle = e.query.toLowerCase(java.util.Locale.ROOT);
			var matches = e.options.stream().filter(o -> o.toLowerCase(java.util.Locale.ROOT).contains(needle)).limit(8).toList();
			var listGrid = new GridLayout().rowSpacing(2);
			var rowHelper = listGrid.createRowHelper(1);
			for (String option : matches) {
				var btn = Button.builder(styledText(option), b -> {
					e.selected = option;
					e.query = "";
					if (e.onChange != null) {
						e.onChange.accept(context, option);
					}
					rebuild();
				}).bounds(0, 0, width, FIELD_HEIGHT).build();
				rowHelper.addChild(btn);
			}
			grid.addChild(listGrid, 1, 0);
		}

		return grid;
	}

	private LayoutElement buildMultiSelectDropdown(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.MultiSelectDropdownElement e) {
		int width = resolveWidth(entry, owner.elementWidth);
		int height = resolveHeight(entry, owner.buttonHeight);
		var grid = new GridLayout().rowSpacing(2);

		String summary = e.selected.isEmpty() ? "Select..." : e.selected.size() + " selected";
		var header = Button.builder(styledText((e.expanded ? "▼ " : "▶ ") + summary), b -> {
			e.expanded = !e.expanded;
			rebuild();
		}).bounds(0, 0, width, height).build();
		grid.addChild(header, 0, 0);

		if (e.expanded) {
			var listGrid = new GridLayout().rowSpacing(2);
			var rowHelper = listGrid.createRowHelper(1);
			for (String option : e.options) {
				boolean checked = e.selected.contains(option);
				var box = Checkbox.builder(Component.literal(option), font)
					.pos(0, 0)
					.maxWidth(width)
					.selected(checked)
					.onValueChange((cb, value) -> {
						if (value) {
							if (!e.selected.contains(option)) {
								e.selected.add(option);
							}
						} else {
							e.selected.remove(option);
						}
						if (e.onChange != null) {
							e.onChange.accept(context, new ArrayList<>(e.selected));
						}
						rebuild();
					})
					.build();
				rowHelper.addChild(box);
			}
			grid.addChild(listGrid, 1, 0);
		}

		return grid;
	}

	private LayoutElement buildSearchBox(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.SearchBoxElement e) {
		var field = new EditBox(font, 0, 0, resolveWidth(entry, owner.elementWidth), resolveHeight(entry, FIELD_HEIGHT), Component.literal(e.id));
		field.setValue(e.query);
		field.setHint(Component.literal("Search..."));
		field.setResponder(text -> {
			e.query = text;
			e.lastKeystrokeAt = System.currentTimeMillis();
			e.pending = true;
		});
		activeSearchBoxes.add(e);
		return field;
	}

	private LayoutElement buildResourcePicker(KubeUIScreenBuilder owner, KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder.ResourcePickerElement e) {
		int width = resolveWidth(entry, owner.elementWidth);
		var grid = new GridLayout().rowSpacing(2);

		var field = new EditBox(font, 0, 0, width, FIELD_HEIGHT, Component.literal(e.id));
		field.setValue(e.query);
		field.setHint(Component.literal(e.selected != null ? e.selected : "Search " + e.kind + "s..."));
		field.setResponder(text -> {
			e.query = text;
			rebuild();
		});
		grid.addChild(field, 0, 0);

		if (!e.query.isEmpty()) {
			String needle = e.query.toLowerCase(java.util.Locale.ROOT);
			var matches = e.allIds.stream().filter(id -> id.contains(needle)).limit(8).toList();
			var listGrid = new GridLayout().rowSpacing(2);
			var rowHelper = listGrid.createRowHelper(1);
			for (String id : matches) {
				rowHelper.addChild(buildResourceRow(e, id, width));
			}
			grid.addChild(listGrid, 1, 0);
		}

		return grid;
	}

	private LayoutElement buildResourceRow(KubeUIScreenBuilder.ResourcePickerElement e, String id, int width) {
		var row = new GridLayout().columnSpacing(4);
		int col = 0;

		if (e.kind.equals("item")) {
			var identifier = net.minecraft.resources.ResourceLocation.parse(id);
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
			if (item != null) {
				row.addChild(new KubeUIItemWidget(0, 0, new ItemStack(item), font, null), 0, col++);
			}
		}

		int btnWidth = Math.max(20, width - (col > 0 ? 20 : 0));
		var btn = Button.builder(styledText(id), b -> {
			e.selected = id;
			e.query = "";
			if (e.onChange != null) {
				e.onChange.accept(context, id);
			}
			rebuild();
		}).bounds(0, 0, btnWidth, FIELD_HEIGHT).build();
		row.addChild(btn, 0, col);

		return row;
	}

	private LayoutElement buildEntityPreview(KubeUIScreenBuilder.Entry entry, KubeUIScreenBuilder owner, KubeUIScreenBuilder.EntityPreviewElement e) {
		int width = resolveWidth(entry, owner.elementWidth);
		int height = resolveHeight(entry, owner.elementWidth);
		var level = Minecraft.getInstance().level;

		if (level != null) {
			var entity = e.entityType().create(level);
			if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
				return new KubeUIEntityPreview(0, 0, width, height, living);
			}
		}

		return new StringWidget(0, 0, width, height, Component.literal("(no entity preview)").withStyle(net.minecraft.ChatFormatting.GRAY), font);
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

	private static final int SNAP_THRESHOLD = 10;

	/// `.snapToEdges()` - pulls `root` flush against whichever screen edge it's already close to
	/// while being dragged, desktop-window-manager style. The top snap target is
	/// `TITLE_BAR_HEIGHT` (not `0`) since the title bar is drawn *above* `root` - snapping `root`
	/// itself to `y = 0` would push the title bar off the top of the screen.
	private void snapRootToScreenEdges() {
		int rootRight = root.getX() + root.getWidth();
		int rootBottom = root.getY() + root.getHeight();

		if (Math.abs(root.getX()) <= SNAP_THRESHOLD) {
			root.setX(0);
		} else if (Math.abs(width - rootRight) <= SNAP_THRESHOLD) {
			root.setX(width - root.getWidth());
		}

		if (Math.abs(root.getY() - TITLE_BAR_HEIGHT) <= SNAP_THRESHOLD) {
			root.setY(TITLE_BAR_HEIGHT);
		} else if (Math.abs(height - rootBottom) <= SNAP_THRESHOLD) {
			root.setY(height - root.getHeight());
		}
	}

	/// Inverse of the `.renderScale(...)` transform pushed in [#extractRenderState] - a 1:1 mirror
	/// of `Matrix3x2f#scaleAround`'s math (`p' = pivot + (p - pivot) * factor`, solved for `p`), so
	/// every mouse-event handler below can work in the same unscaled coordinate space every
	/// widget's `getX()`/`getY()`/`getWidth()`/`getHeight()` already live in.
	private MouseButtonEvent correctForRenderScale(MouseButtonEvent event) {
		if (builder.renderScale == 1.0f || root == null) {
			return event;
		}
		float pivotX = root.getX() + root.getWidth() / 2f;
		float pivotY = root.getY() + root.getHeight() / 2f;
		double correctedX = pivotX + (event.x() - pivotX) / builder.renderScale;
		double correctedY = pivotY + (event.y() - pivotY) / builder.renderScale;
		return new MouseButtonEvent(correctedX, correctedY, event.button());
	}

	private final DoubleClickTracker doubleClickTracker = new DoubleClickTracker();

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return mouseClicked(new MouseButtonEvent(mouseX, mouseY, button), doubleClickTracker.registerClick(mouseX, mouseY));
	}

	private boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		event = correctForRenderScale(event);

		if (activeContextMenu != null) {
			boolean handled = activeContextMenu.mouseClicked(event.x(), event.y(), event.button());
			dismissContextMenu();
			if (handled) {
				return true;
			}
			// click was outside the menu - fall through and let it hit whatever's underneath
		}

		if (event.button() == 1) {
			for (var widget : allWidgets) {
				var spec = contextMenus.get(widget);
				if (spec != null && widget.isMouseOver(event.x(), event.y())) {
					openContextMenu(spec, (int) event.x(), (int) event.y());
					return true;
				}
			}
		}

		if (builder.draggable && titleBarRect.containsPoint((int) event.x(), (int) event.y())) {
			draggingWindow = true;
			dragStartMouseX = event.x();
			dragStartMouseY = event.y();
			dragStartOffsetX = dragOffsetX;
			dragStartOffsetY = dragOffsetY;
			return true;
		}

		if (event.button() == 0) {
			var candidate = widgetAt(event.x(), event.y());
			if (candidate != null && dragSources.containsKey(candidate)) {
				dragCandidateWidget = candidate;
				dragCandidateStartX = event.x();
				dragCandidateStartY = event.y();
			}
		}

		boolean handled = super.mouseClicked(event.x(), event.y(), event.button());

		if (doubleClick) {
			var target = widgetAt(event.x(), event.y());
			if (target != null && doubleClickHandlers.containsKey(target)) {
				doubleClickHandlers.get(target).accept(context);
			}
		}

		return handled;
	}

	private AbstractWidget widgetAt(double x, double y) {
		for (var widget : allWidgets) {
			if (widget.isMouseOver(x, y)) {
				return widget;
			}
		}
		return null;
	}

	private void openContextMenu(ContextMenuSpec spec, int x, int y) {
		List<String> items = spec.itemsSupplier() != null ? spec.itemsSupplier().apply(context) : spec.items();
		if (items == null || items.isEmpty()) {
			return;
		}

		activeContextMenu = new KubeUIContextMenuWidget(x, y, items, font, index -> {
			String item = items.get(index);
			if (spec.onSelect() != null) {
				spec.onSelect().accept(context, item);
			}
		});
		addRenderableWidget(activeContextMenu);
		setFocused(activeContextMenu);
	}

	private void dismissContextMenu() {
		if (activeContextMenu != null) {
			removeWidget(activeContextMenu);
			activeContextMenu = null;
		}
	}

	/// `.hoverPreview(...)`: tracks how long each registered widget has been continuously hovered,
	/// opening the popover once one crosses its own delay (at most one shown at a time), and closing
	/// it the moment its widget stops being hovered.
	private void tickHoverPreviews(long now) {
		for (var widget : hoverPreviews.keySet()) {
			if (widget.isHovered()) {
				hoverStartTimes.putIfAbsent(widget, now);
			} else {
				hoverStartTimes.remove(widget);
				if (widget == activeHoverPreviewWidget) {
					dismissHoverPreview();
				}
			}
		}

		if (activeHoverPreviewWidget == null) {
			for (var entry : hoverPreviews.entrySet()) {
				Long since = hoverStartTimes.get(entry.getKey());
				if (since != null && now - since >= entry.getValue().delayMs()) {
					showHoverPreview(entry.getKey(), entry.getValue());
					break;
				}
			}
		}
	}

	private void showHoverPreview(AbstractWidget widget, HoverPreviewSpec spec) {
		var nested = KubeUIScreenBuilder.freshChildBuilder(builder);
		spec.builder().accept(nested);
		var layout = buildContainer(nested, 1);
		layout.arrangeElements();
		layout.setX(widget.getX());
		layout.setY(widget.getY() + widget.getHeight() + 2);
		layout.visitWidgets(this::addRenderableWidget);

		activeHoverPreviewWidget = widget;
		activeHoverPreviewLayout = layout;
	}

	private void dismissHoverPreview() {
		if (activeHoverPreviewLayout != null) {
			activeHoverPreviewLayout.visitWidgets(this::removeWidget);
			activeHoverPreviewLayout = null;
		}
		activeHoverPreviewWidget = null;
	}

	private static final double DRAG_START_THRESHOLD = 4.0;

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		return mouseDragged(new MouseButtonEvent(mouseX, mouseY, button), dx, dy);
	}

	private boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		event = correctForRenderScale(event);
		dx /= builder.renderScale;
		dy /= builder.renderScale;

		if (activeDragWidget == null && dragCandidateWidget != null) {
			double moved = Math.hypot(event.x() - dragCandidateStartX, event.y() - dragCandidateStartY);
			if (moved >= DRAG_START_THRESHOLD) {
				activeDragWidget = dragCandidateWidget;
				activeDragPayload = dragSources.get(dragCandidateWidget);
				dragCandidateWidget = null;
			}
		}

		if (activeDragWidget != null) {
			return true;
		}

		if (draggingWindow) {
			dragOffsetX = dragStartOffsetX + (int) (event.x() - dragStartMouseX);
			dragOffsetY = dragStartOffsetY + (int) (event.y() - dragStartMouseY);

			root.setX(baseX + dragOffsetX);
			root.setY(baseY + dragOffsetY);

			if (builder.snapToEdges) {
				snapRootToScreenEdges();
				// Snapping only moved `root` - fold that back into the offset it's normally
				// computed from, or the *next* drag tick would immediately undo it.
				dragOffsetX = root.getX() - baseX;
				dragOffsetY = root.getY() - baseY;
			}

			titleBarRect = new ScreenRectangle(new ScreenPosition(root.getX(), root.getY() - TITLE_BAR_HEIGHT), root.getWidth(), TITLE_BAR_HEIGHT);

			if (resizeHandle != null) {
				resizeHandle.setX(root.getX() + root.getWidth() - RESIZE_HANDLE_SIZE);
				resizeHandle.setY(root.getY() + root.getHeight() - RESIZE_HANDLE_SIZE);
			}

			return true;
		}

		return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		return mouseReleased(new MouseButtonEvent(mouseX, mouseY, button));
	}

	private boolean mouseReleased(MouseButtonEvent event) {
		event = correctForRenderScale(event);

		dragCandidateWidget = null;

		if (activeDragWidget != null) {
			var target = widgetAt(event.x(), event.y());
			var onDrop = target != null ? dropTargets.get(target) : null;
			if (onDrop != null) {
				onDrop.accept(context, activeDragPayload);
			}
			activeDragWidget = null;
			activeDragPayload = null;
			return true;
		}

		if (draggingWindow) {
			draggingWindow = false;
			return true;
		}

		return super.mouseReleased(event.x(), event.y(), event.button());
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		double correctedX = mouseX;
		double correctedY = mouseY;

		if (builder.renderScale != 1.0f && root != null) {
			float pivotX = root.getX() + root.getWidth() / 2f;
			float pivotY = root.getY() + root.getHeight() / 2f;
			correctedX = pivotX + (mouseX - pivotX) / builder.renderScale;
			correctedY = pivotY + (mouseY - pivotY) / builder.renderScale;
		}

		boolean handled = super.mouseScrolled(correctedX, correctedY, scrollX, scrollY);

		if (handled) {
			for (var area : scrollAreas) {
				if (correctedX >= area.getX() && correctedX < area.getX() + area.getWidth()
					&& correctedY >= area.getY() && correctedY < area.getY() + area.getHeight()) {
					scrollVelocity.merge(area, scrollY * SCROLL_MOMENTUM_KICK, Double::sum);
					break;
				}
			}
		}

		return handled;
	}

	private Component styledText(String text) {
		var component = Component.literal(text);
		if (builder.customFont != null) {
			component = component.withStyle(net.minecraft.network.chat.Style.EMPTY.withFont(builder.customFont));
		}
		return component;
	}

	private void playSound(ResourceLocation id) {
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
		if (entry.style.responsiveWidth != null) {
			return resolveResponsiveWidth(entry.style.responsiveWidth, fallback);
		}
		if (entry.style.widthPercent != null) {
			return Math.round(entry.style.widthPercent * width);
		}
		int px = KubeUILayoutMath.resolveWidth(entry.style.width, fallback);
		return Math.max(1, Math.round(px * KubeUITheme.uiScale));
	}

	/// Backs `.width({default, small, tiny})` - picks the tier matching the screen's current
	/// logical width (re-evaluated on every `rebuild()`/resize), falling back to `"default"` if
	/// the matching tier wasn't specified. Each tier's value is either a pixel count (any `Number`
	/// - Rhino hands JS numbers over as `Double`/`Integer` depending on the script) or a `"NN%"`
	/// string, same two shapes [#width(int)]/[#width(String)] already accept individually.
	private int resolveResponsiveWidth(Map<String, Object> breakpoints, int fallback) {
		String tier = width < 400 ? "tiny" : width < 640 ? "small" : "default";
		Object value = breakpoints.get(tier);
		if (value == null) {
			value = breakpoints.get("default");
		}

		if (value instanceof Number n) {
			return Math.max(1, Math.round(n.floatValue() * KubeUITheme.uiScale));
		}

		if (value instanceof String s) {
			Float pct = KubeUILayoutMath.parsePercent(s);
			if (pct != null) {
				return Math.round(pct * width);
			}
		}

		return fallback;
	}

	private int resolveHeight(KubeUIScreenBuilder.Entry entry, int fallback) {
		if (entry.style.heightPercent != null) {
			return Math.round(entry.style.heightPercent * height);
		}
		int px = KubeUILayoutMath.resolveHeight(entry.style.height, fallback);
		return Math.max(1, Math.round(px * KubeUITheme.uiScale));
	}

	/// Raw linear open/close progress, `[0, 1]` - `0` = fully closed/not-yet-shown, `1` = fully
	/// open. Shared basis for both the alpha fade and the slide/scale transforms below.
	private float animationT() {
		if (!builder.animated) {
			return 1f;
		}

		long now = System.currentTimeMillis();

		if (closing) {
			float t = Math.min(1f, (now - closeStartTime) / (float) builder.animationDurationMs);
			return 1f - t;
		}

		return Math.min(1f, (now - openStartTime) / (float) builder.animationDurationMs);
	}

	/// [#animationT()] run through `.animated(...)`'s `easing` curve.
	private float animationEasedT() {
		float t = animationT();
		if (!"easeInOut".equals(builder.animationEasing)) {
			return t;
		}
		// Standard cubic ease-in-out: slow start, fast middle, slow end.
		return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
	}

	/// `"dirt"` is the only mode that wants the vanilla full-menu background path (dirt texture,
	/// optional panorama, blur gated on the player's own option) - handled by the real
	/// `renderBackground` override below calling `super.renderBackground(...)` before
	/// [#extractBackground] ever runs. Everything else (`"blur"`, `"none"`, `"texture"`) draws its
	/// own simpler background in [#extractBackground] instead.
	@Override
	public void renderBackground(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float a) {
		if ("dirt".equals(builder.backgroundMode)) {
			super.renderBackground(realGraphics, mouseX, mouseY, a);
		}
		extractBackground(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, a);
	}

	private void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		switch (builder.backgroundMode) {
			case "texture" -> {
				if (builder.backgroundTexture != null) {
					graphics.blit(RenderPipelines.GUI_TEXTURED, builder.backgroundTexture, 0, 0, 0, 0, width, height, width, height);
				}
			}
			case "blur" -> {
				graphics.blurBeforeThisStratum();
				this.renderTransparentBackground(graphics.real());
			}
			case "none" -> this.renderTransparentBackground(graphics.real());
			default -> {
				// "dirt" already handled above by the real renderBackground override.
			}
		}

		extractWindowBackground(graphics);
	}

	/// The real "behind the content" panel `.windowBackground(...)` asks for - drawn here (part of
	/// [#extractBackground], which vanilla's own `Screen` lifecycle already calls before any child
	/// widget renders) rather than as a widget in the tree, since that's the only place in this
	/// screen's render pipeline that's guaranteed to paint before, not after, the actual quest/
	/// recipe/whatever text sitting on top of it. Sized a few pixels larger than `root` on every
	/// side so the border reads as a frame around the content rather than clipping straight through
	/// it, and - for a `.draggable()` screen - extended upward to also back the title bar, so the
	/// title and its content read as one unified panel instead of a floating label above a
	/// separately-framed box.
	private static final int WINDOW_BACKGROUND_PADDING = 6;

	private void extractWindowBackground(GuiGraphicsExtractor graphics) {
		if (builder.windowBackgroundTexture == null || root == null) {
			return;
		}

		int pad = WINDOW_BACKGROUND_PADDING;
		int x = root.getX() - pad;
		int y = root.getY() - pad - (builder.draggable ? TITLE_BAR_HEIGHT : 0);
		int w = root.getWidth() + pad * 2;
		int h = root.getHeight() + pad * 2 + (builder.draggable ? TITLE_BAR_HEIGHT : 0);
		KubeUIPanelBackground.draw(graphics, builder.windowBackgroundTexture, x, y, w, h);
	}

	@Override
	public void render(net.minecraft.client.gui.GuiGraphics realGraphics, int mouseX, int mouseY, float delta) {
		this.renderBackground(realGraphics, mouseX, mouseY, delta);
		extractRenderState(new GuiGraphicsExtractor(realGraphics), mouseX, mouseY, delta);
	}

	private void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		boolean hasRenderScale = builder.renderScale != 1.0f && root != null;
		boolean hasTransitionTransform = builder.animated && !"fade".equals(builder.animationType) && root != null;
		boolean hasShake = shakeStartTime != 0 && System.currentTimeMillis() - shakeStartTime < SHAKE_DURATION_MS;

		if (!hasRenderScale && !hasTransitionTransform && !hasShake) {
			extractScaledRenderState(graphics, mouseX, mouseY, delta);
			return;
		}

		float pivotX = root != null ? root.getX() + root.getWidth() / 2f : width / 2f;
		float pivotY = root != null ? root.getY() + root.getHeight() / 2f : height / 2f;
		int correctedMouseX = mouseX;
		int correctedMouseY = mouseY;

		graphics.pose().pushMatrix();

		if (hasRenderScale) {
			graphics.pose().scaleAround(builder.renderScale, pivotX, pivotY);
			correctedMouseX = Math.round(pivotX + (mouseX - pivotX) / builder.renderScale);
			correctedMouseY = Math.round(pivotY + (mouseY - pivotY) / builder.renderScale);
		}

		// Slide/scale/shake are brief (~200-300ms) cosmetic transforms - unlike `.renderScale(...)`,
		// a *persistent* per-screen setting, they're not worth the same mouse-coordinate correction
		// treatment for a window this short-lived.
		if (hasTransitionTransform) {
			float t = animationEasedT();
			if ("scale".equals(builder.animationType)) {
				graphics.pose().scaleAround(0.85f + 0.15f * t, pivotX, pivotY);
			} else if ("slide".equals(builder.animationType)) {
				graphics.pose().translate(0, (1f - t) * 24f);
			}
		}

		if (hasShake) {
			float t = (System.currentTimeMillis() - shakeStartTime) / (float) SHAKE_DURATION_MS;
			float decay = 1f - t;
			graphics.pose().translate((float) (Math.sin(t * Math.PI * 6) * 6 * decay), 0);
		}

		extractScaledRenderState(graphics, correctedMouseX, correctedMouseY, delta);
		graphics.pose().popMatrix();
	}

	/// The screen's actual content, drawn inside whatever `.renderScale(...)` transform (if any)
	/// [#extractRenderState] pushed around it - `mouseX`/`mouseY` here are already corrected into
	/// that same (unscaled) coordinate space, so every widget's own hover check stays accurate.
	void extractScaledRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		float crossFadeIn = 1f;
		if (crossFadeFrom != null) {
			crossFadeIn = crossFadeT();
			if (crossFadeIn < 1f) {
				for (var widget : crossFadeFrom.allWidgets) {
					widget.setAlpha(1f - crossFadeIn);
				}
				crossFadeFrom.extractScaledRenderState(graphics, -1, -1, delta);
			} else {
				crossFadeFrom = null;
			}
		}

		for (var renderable : this.renderables) {
			renderable.render(graphics.real(), mouseX, mouseY, delta);
		}

		float alpha = Math.min(builder.animated ? animationEasedT() : 1f, crossFadeIn);
		if (alpha < 1f) {
			for (var widget : allWidgets) {
				widget.setAlpha(alpha);
			}
		}

		if (builder.draggable && root != null) {
			graphics.centeredText(font, getTitle().getString(), root.getX() + root.getWidth() / 2, root.getY() - TITLE_BAR_HEIGHT + 4, KubeUITheme.titleColor());
		} else {
			graphics.centeredText(font, getTitle().getString(), width / 2, 12, KubeUITheme.titleColor());
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

		if (KubeUIDebug.gridEnabled) {
			extractDebugGrid(graphics);
		}

		if (activeDragWidget != null) {
			for (var target : dropTargets.keySet()) {
				if (target.isHovered()) {
					graphics.outline(target.getX() - 1, target.getY() - 1, target.getWidth() + 2, target.getHeight() + 2, 0xFF45D6C9);
				}
			}
			String label = String.valueOf(activeDragPayload);
			graphics.fill(mouseX + 8, mouseY + 4, mouseX + 8 + font.width(label) + 6, mouseY + 4 + font.lineHeight + 4, 0xE0202020);
			graphics.text(font, label, mouseX + 11, mouseY + 6, 0xFFEAF3F3, false);
		}

		if (shakeStartTime != 0) {
			long elapsed = System.currentTimeMillis() - shakeStartTime;
			if (elapsed < SHAKE_DURATION_MS) {
				float t = elapsed / (float) SHAKE_DURATION_MS;
				int flashAlpha = Math.round(0x50 * (1f - t));
				graphics.fill(0, 0, width, height, (flashAlpha << 24) | 0xFF5555);
			} else {
				shakeStartTime = 0;
			}
		}
	}

	private static final int DEBUG_GRID_SPACING = 20;

	/// `/kubeui grid` - a light reference grid across the whole screen, plus each visible widget's
	/// own pixel dimensions labeled at its top-left corner. Independent of `/kubeui outline` - the
	/// two can be toggled together or separately.
	private void extractDebugGrid(GuiGraphicsExtractor graphics) {
		for (int x = 0; x < width; x += DEBUG_GRID_SPACING) {
			graphics.fill(x, 0, x + 1, height, 0x22FFFFFF);
		}
		for (int y = 0; y < height; y += DEBUG_GRID_SPACING) {
			graphics.fill(0, y, width, y + 1, 0x22FFFFFF);
		}

		for (var widget : allWidgets) {
			if (!widget.visible) {
				continue;
			}
			String dimensions = widget.getWidth() + "x" + widget.getHeight();
			graphics.text(font, dimensions, widget.getX() + 1, widget.getY() - font.lineHeight, 0xFF55FFFF, true);
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

	static final class DatePickerHandle {
		int year;
		int month;
		int day;
		EditBox yearField;
		EditBox monthField;
		EditBox dayField;

		DatePickerHandle(int year, int month, int day) {
			this.year = year;
			this.month = month;
			this.day = day;
		}
	}
}
