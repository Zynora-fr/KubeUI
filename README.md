# KubeUI

A [KubeJS](https://kubejs.com) addon for building interactive, widget-based graphical
user interfaces (buttons, and more to come) directly from JS scripts.

## Why NeoForge only?

`KubeUI.md` originally called for Fabric + Forge + NeoForge support. That turned out to
be impossible to honor together with "KubeJS addon", because KubeJS itself no longer
supports all three at once:

- KubeJS `2001` branch (Minecraft 1.20.1) ships `common` + `fabric` + `forge` only - no NeoForge.
- Every KubeJS branch from `2101` (Minecraft 1.21.1) onward, including the current
  `2601` branch (Minecraft 26.1.2), is NeoForge-only.

There is no Minecraft version where KubeJS publishes builds for all three loaders. KubeUI
targets **Minecraft 26.1.2 / NeoForge**, matching KubeJS's current, actively maintained line.

## Why this fills a real gap

KubeJS's own built-in GUI system (`dev.latvian.mods.kubejs.gui`) only supports chest-style
container menus - a fixed grid of item slots. There is no built-in way for a script to open
a screen with buttons or other free-form widgets. KubeUI adds that.

## Using it from a KubeJS script

Every callback (`button`, `toggle`, `textField`) receives a `screen` argument - a handle to
the *open* screen - so it can update other widgets without rebuilding the whole thing.

```js
// client_scripts
KubeUI.builder('My Menu')
    .label('status', 'Pick something')
    .button('Say Hello', screen => screen.setLabel('status', 'Hello!'))
    .toggle('flag', 'Enable thing', false, (screen, value) => {
        screen.setLabel('status', 'Thing: ' + value)
    })
    .textField('name', '', 'Your name...', (screen, value) => {
        screen.setLabel('status', 'Hi, ' + value)
    })
    .button('Close', screen => screen.close())
    .open()
```

Available widgets:

| Method | Description |
|---|---|
| `.label(id, text)` | A text line. Updatable later via `screen.setLabel(id, text)`. |
| `.button(text, onClick)` | `onClick(screen)` fires on click. |
| `.toggle(id, text, initial, onChange)` | A checkbox. `onChange(screen, value)` fires when toggled. |
| `.textField(id, initialValue, hint, onChange)` | A single-line text box. `onChange(screen, value)` fires on every keystroke. |
| `.slider(id, min, max, initial, onChange)` | A slider over `[min, max]`. `onChange(screen, value)` fires while dragging. |
| `.dropdown(id, options, initial, onChange)` | A click/scroll-to-cycle select over a list of strings. |
| `.radioGroup(id, options, initial, onChange)` | An exclusive (single-select) group of options, stacked vertically. |
| `.textArea(id, initialValue, hint, height, onChange)` | A multi-line text box, `height` pixels tall. |
| `.image(texture, width, height)` | A static texture (an `Identifier`/resource path string), scaled to fill the box. Not updatable. |
| `.item(item, count)` / `.item(item, count, onClick)` | An item icon with its count badge, rendered like an inventory slot. Clickable (with a hover highlight) only if `onClick` is given. |
| `.progressBar(id, value, max)` | A read-only progress bar. Update it via `screen.setProgress(id, value)`. |
| `.divider()` | A thin horizontal separator line. |
| `.number(id, min, max, initial, onChange)` | An integer spinner (`-` / value / `+`), clamped to `[min, max]`. |
| `.colorPicker(id, initial, onChange)` | A row of preset color swatches plus a `#RRGGBB` hex field. `initial`/the value passed to `onChange` are opaque ARGB values (e.g. `0xFFff5555`). |
| `.checkboxGroup(id, options, initialSelected, onChange)` | An independent multi-select group of checkboxes. `onChange(screen, values)` receives the full list of currently checked options. |
| `.elementSize(width, buttonHeight)` | Overrides the column width / button height for elements added after this call. |

`screen` (the [`KubeUIContext`](src/main/java/dev/kubeui/gui/KubeUIContext.java) passed to every
callback) also exposes matching getters/setters for every stateful widget above: `getTextFieldValue`/
`setTextFieldValue`, `getTextAreaValue`/`setTextAreaValue`, `getSliderValue`/`setSliderValue`,
`getDropdownValue`/`setDropdownValue`, `getRadioValue`/`setRadioValue`, `getNumberValue`/
`setNumberValue`, `getColor`/`setColor`, `getCheckboxGroupValues`, and `getProgress`/`setProgress`.
`KubeUI.close()` (no `screen` needed) closes whatever KubeUI screen is currently open, from anywhere.

> **Note on big hex colors:** `colorPicker`'s `initial` parameter (and `screen.setColor`) take a
> `long`, not an `int`. A fully-opaque color literal like `0xFFff5555` is bigger than
> `Integer.MAX_VALUE`, and Rhino won't silently wrap a JS number into a Java `int` the way Java's
> own overflow rules would - it throws instead. `long` has enough range to accept the literal as-is.

## Layout

By default every element stacks vertically, centered on screen. Containers and per-element
modifiers change that:

| Method | Description |
|---|---|
| `.row(children)` | Lays out a group of elements horizontally instead of stacking them. `children` receives a *nested* builder (same class, reused recursively) to populate. |
| `.grid(columns, children)` | Lays out a group of elements in a grid with `columns` columns, wrapping automatically. |
| `.scrollPanel(maxHeight, children)` | Wraps a vertically-stacked group in a scrollable viewport `maxHeight` pixels tall (mouse wheel to scroll). |
| `.list(id, items, renderer)` | Adds one row per entry in `items` (any script values), calling `renderer(row, item, index)` to build each one. |
| `.anchor(preset)` / `.anchor(preset, marginX, marginY)` | Positions the screen's content instead of centering it. `preset`: `"center"` (default), `"top"`, `"bottom"`, `"left"`, `"right"`, or a corner like `"top-left"`. Root builder only. |

Four modifiers apply to whichever element was added **most recently** - chain them right after it:

| Method | Description |
|---|---|
| `.width(w)` | Overrides that element's width (instead of the builder's `elementWidth`). |
| `.height(h)` | Overrides that element's height (instead of its type's default height). |
| `.align(horizontal)` | `"left"`, `"center"` or `"right"` - where the element sits if it's narrower than the space allotted to it. |
| `.padding(...)` | `padding(all)`, `padding(horizontal, vertical)`, or `padding(left, top, right, bottom)`, in pixels. |

```js
KubeUI.builder('Layout Demo')
    .row(row => row
        .button('Yes', screen => screen.setLabel('status', 'Yes!')).width(80)
        .button('No', screen => screen.setLabel('status', 'No!')).width(80).align('right'))
    .grid(3, grid => grid
        .button('A', s => {}).width(50)
        .button('B', s => {}).width(50)
        .button('C', s => {}).width(50)
        .button('D', s => {}).width(50))
    .scrollPanel(80, panel => panel
        .radioGroup('team', ['Red', 'Blue', 'Green'], 'Red', (s, v) => {}))
    .list('fruits', ['Apple', 'Banana'], (row, item, index) => {
        row.label('name_' + index, item).width(100)
        row.button('Pick', s => s.setLabel('status', 'Picked ' + item)).width(60)
    })
    .anchor('top-right', 10, 10)
    .open()
```

Under the hood, this is compiled into a tree of vanilla `net.minecraft.client.gui.layouts.GridLayout`
/ `net.minecraft.client.gui.components.ScrollableLayout` instances - KubeUI only builds the leaf
widgets; sizing, padding, alignment and scrolling are handled by the same code vanilla Minecraft's
own option screens use.

### Responsive sizing & scale

Not every player has the same window size, resolution or GUI Scale setting - a screen that looks
right on the machine it was written on can end up cut off or oddly proportioned on someone else's.
Three things help with that:

| Feature | What it does |
|---|---|
| `.width("50%")` / `.height("30%")` | Same as `.width(200)`/`.height(80)`, but as a percentage of the screen's *current* size instead of a fixed pixel count - stays proportional across any window size/resolution. Falls back to the pixel default (with a logged error) if the string isn't a valid `"<number>%"`. |
| Automatic scroll safety net | Any screen the scripter didn't explicitly mark `.resizable(...)` still gets measured against the actual window size when it opens - if its natural content is taller than what's available, it's transparently wrapped in a scroll area instead of rendering off the bottom of the screen and becoming unreachable. Nothing to opt into; it only kicks in when needed. |
| `KubeUI.setScale(factor)` / `.resetScale()` / `.getScale()` | A personal multiplier (0.5-2.0) on every screen's pixel-based widths/heights, independent of both the script and Minecraft's own GUI Scale option - for "this UI is too big/small on my particular monitor". Percentages aren't affected by it (a `"50%"`-wide element stays 50% of the screen regardless of scale). Players can also set this themselves with `/kubeui scale <factor>` (see [Debugging](#debugging)) without any script support. |

```js
KubeUI.builder('Responsive Example')
    .row(row => row.button('90% wide', s => {}).width('90%'))
    .open()
```

## Screens & UX

| Method | Description |
|---|---|
| `.tab(name, children)` | Adds a tab (a tab bar switches between them). Replaces the builder's own content entirely - don't mix `.tab(...)` with `.button(...)`/etc. on the root builder. |
| `.draggable()` | The content becomes draggable by its title. Root builder only. |
| `.resizable(minHeight, maxHeight)` | Adds a corner handle that resizes the content's visible height (scrolling if content is taller). Root builder only. |
| `.tooltip(text)` | Shows `text` in a tooltip when hovering the last-added element. |
| `.tabOrder(n)` | Explicit Tab-key focus order (lower first) for the last-added element. |
| `.narration(text)` | Custom screen-reader text for the last-added element - only takes effect on elements that don't already narrate something meaningful (`divider`, `spacer`, `progressBar`, `image`, `item`); interactive vanilla widgets ignore it. |
| `.id(x)` | Assigns an id to the last-added element, for `screen.setEnabled`/`setVisible`/`.remove(...)`, on element types that don't already take one (`button`, `image`, `item`, `divider`, `spacer`). |

`screen` additionally exposes:

| Method | Description |
|---|---|
| `screen.setEnabled(id, bool)` | Enables/disables (greys out) the element with this id. Affects every widget inside a composite element. |
| `screen.setVisible(id, bool)` | Shows/hides the element with this id (its layout space is still reserved). |
| `screen.update(mutator)` | Calls `mutator(builder)` on the screen's own builder, then rebuilds - use with `.button(...)`/etc. to add elements, or `.remove(id)` to delete one, after the screen is already open. Rebuilding recreates every widget from scratch (typed text, scroll position, etc. for elements that survive the update are not preserved). |

Confirmation/alert dialogs open on top of whatever's currently on screen (including another KubeUI
screen) and return to it once dismissed:

```js
KubeUI.confirm('Are you sure?', 'This will do a thing.\nContinue?',
    () => KubeUI.alert('Confirmed', 'You said yes.', null),
    () => KubeUI.alert('Cancelled', 'You said no.', null))
```

```js
KubeUI.builder('Dashboard')
    .draggable()
    .resizable(60, 240)
    .tab('Info', tab => tab.label('hint', 'Drag the title to move, the corner to resize.'))
    .tab('Actions', tab => tab
        .toggle('enableToggle', 'Enable target', true, (screen, value) => screen.setEnabled('target', value))
        .button('Target', screen => console.log('clicked')).id('target').tooltip('Hover me'))
    .open()
```

> **Verification note:** `.row`/`.grid`/`.scrollPanel`/`.list`/`.tab`/`.draggable`/`.resizable` were
> checked by compiling against the real (decompiled) Minecraft 26.1.2 sources for every API used
> (`GridLayout`, `ScrollableLayout`, `FrameLayout`, `ContainerEventHandler`'s mouse handling,
> `Tooltip`, `ScreenRectangle`) and by exercising the builder call chains through KubeJS/Rhino. The
> actual widget tree (`KubeUIScreen.rebuild()`) can only run once `Minecraft.getInstance()` is
> populated, which only happens once the game is fully running - something this sandbox can't
> automate past reaching the main menu. If you hit an issue with tabs, dragging, resizing, or
> modals specifically, that's the part least exercised by automated testing - please report it.

## Behavior & polish

| Method | Description |
|---|---|
| `.animated()` / `.animated(durationMs)` | Fades widgets in on open and out on close (200ms default). Root builder only. Only affects vanilla widgets (button, checkbox, slider, ...) - KubeUI's own flat-fill widgets (divider, progress bar, ...) don't fade. |
| `.onOpen(callback)` | Called once, right after the screen finishes opening. |
| `.onClose(callback)` | Called once, right before the screen actually closes, for any reason. |
| `.bind(supplier, onChange)` | Polls `supplier` every client tick; calls `onChange(screen, value)` whenever the returned value changes (by equality). Drives a widget from external, changing state without writing your own tick handler. |
| `.font(fontId)` | Renders every label/button on this screen with a resource-pack font (e.g. `"minecraft:alt"`) instead of the default. Root builder only - affects the whole screen. |
| `.sound(soundId)` | Plays a sound (in addition to the default click sound) when the last-added element is interacted with. Supported on `button`, `toggle`, `item`. |
| `.custom(type, ...args)` | Adds a widget registered by a third-party **Java** mod via `KubeUIWidgets.register(type, factory)` (see [`KubeUIWidgetFactory`](src/main/java/dev/kubeui/gui/KubeUIWidgetFactory.java)) - not something a script defines itself. Shows a visible placeholder if `type` isn't registered. |
| `KubeUI.builder(title, persistKey)` | Like `.builder(title)`, but text fields/sliders/toggles/etc. remember their values across separate `.open()` calls sharing the same `persistKey`. |
| `KubeUI.setTheme(titleColor, accentColor, textColor)` / `KubeUI.resetTheme()` | Global colors (ARGB `long`s) applied to screens built from now on - only affects what KubeUI draws directly (title, progress bar fill); vanilla widgets keep their own sprites. |

```js
KubeUI.builder('Live Dashboard')
    .animated()
    .label('hp', 'HP: ?')
    .bind(() => Player.health, (screen, value) => screen.setLabel('hp', 'HP: ' + value))
    .onOpen(screen => console.log('Dashboard opened'))
    .onClose(screen => console.log('Dashboard closed'))
    .open()
```

### A note on `KubeUIContext` and rebuilds

Every `screen.setXxx(...)` method mutates only the one widget it targets - none of them call
`rebuild()`. `screen.update(mutator)` is the sole exception: adding/removing elements changes the
widget tree itself, so it necessarily rebuilds everything (this was audited across every
`KubeUIContext` method while implementing this - see `KubeUIScreen`'s class doc).

### Unit tests

Pure logic with no Minecraft dependency (`resolveWidth`/`resolveHeight` fallback resolution,
`clampInt`, hex color parsing/formatting) lives in
[`KubeUILayoutMath`](src/main/java/dev/kubeui/gui/KubeUILayoutMath.java), with real JUnit 5 tests
in [`src/test/java`](src/test/java/dev/kubeui/gui/KubeUILayoutMathTest.java) - `./gradlew test`.
These don't need the Minecraft/NeoForge toolchain at all, unlike everything else in this project.

### Callbacks never crash the client

Every callback KubeUI takes from a script (`button`, `toggle`, ..., `onOpen`/`onClose`, `bind`,
`confirm`/`alert`, `list`'s renderer) is wrapped in a try/catch the moment it's handed to the
builder. A bug in a script's callback logs an error (`KubeUI: error in a widget callback`, with
the stack trace) instead of propagating out through Minecraft's input handling and taking the
whole client down with it.

## Server integration

Two ways for a script to affect real game state from a screen, with very different trust models:

| Method | Trust model |
|---|---|
| `screen.giveItem(item, count)` | Runs a client-sent `/give` command. Gated only by the player's own command permissions (cheats/op) - fine for simple/single-player cases, but a modified client could in principle send whatever it wants. |
| `screen.runServerAction(id, data)` | Sends `id` + `data` to the server as a packet; a `server_scripts` handler registered via `KubeUIActions.register(id, (player, data) => {...})` decides entirely what happens. The client never gets to assert a result (a price, a success) - the handler looks up the truth itself. Unregistered ids do nothing (not even an error) on the server. |

```js
// server_scripts
KubeUIActions.register('myaddon:claim_reward', (player, data) => {
    // validate whatever matters yourself - player's actual state, not `data`
    player.give('minecraft:diamond')
})
```

```js
// client_scripts
screen.runServerAction('myaddon:claim_reward', {})
```

See [`testkubejs/server_scripts/kubeui_actions.js`](testkubejs/server_scripts/kubeui_actions.js)
and the "Buy (Server-Validated)" button in
[`kubeui_test_shop.js`](testkubejs/client_scripts/kubeui_test_shop.js) for a minimal example (the
server re-derives the real price instead of trusting whatever the client sends).

For what this looks like on an actual survival server -
[`server_scripts/kubeui_shop_real.js`](testkubejs/server_scripts/kubeui_shop_real.js) +
[`client_scripts/kubeui_shop_real.js`](testkubejs/client_scripts/kubeui_shop_real.js): currency is
real emeralds pulled from the player's own inventory (`player.inventory.count(...)`/
`.extractItem(...)`, not a JS number that forgets itself on relog), a simulated
`insertItem(stack, true)` checks there's room *before* any emeralds are taken, and every sale is
logged server-side with `console.log`. Opened by right-clicking an Emerald Block in-world (a
stand-in for a shop sign/NPC), so it behaves like something you'd actually place on a server
rather than a menu button that only exists for this test project.

`KubeUIEvents.screenOpen(event => {...})` / `.screenClose(...)` additionally fire for *every*
KubeUI screen (not just the one you built) - useful for an addon reacting to any KubeUI screen
appearing, as opposed to `.onOpen(...)`/`.onClose(...)` which only fire for that one screen.

## Sidebar (survival inventory icon bar)

A Paladium-style column of clickable icons next to the vanilla survival inventory screen (`E`) -
for menus that should always be one click away, without a keybind or a block to place. Registered
once, typically at script load; the icons then show up automatically every time the inventory
screen opens.

| Method | Description |
|---|---|
| `KubeUISidebar.addItem(id, item, tooltip, onClick)` | Adds (or replaces, keeping its position) an icon rendered as a vanilla item, like an inventory slot. `tooltip` may be null/empty. |
| `KubeUISidebar.addTexture(id, texture, tooltip, onClick)` | Same, but rendered from a custom resource-pack texture instead of an item. |
| `KubeUISidebar.remove(id)` / `.clear()` | Removes one icon, or every icon. |

```js
// client_scripts
KubeUISidebar.addItem('myaddon:shop', 'minecraft:emerald', 'Open the shop', () => openShop())
```

`onClick` is a plain callback (not a `KubeUIContext` - there's no KubeUI screen open yet at that
point) and is safety-wrapped the same way every other KubeUI callback is (see
[Callbacks never crash the client](#callbacks-never-crash-the-client)).

Implemented via NeoForge's `ScreenEvent.Init.Post` (fired after `InventoryScreen` finishes its own
`init()`) rather than a mixin - see
[`KubeUISidebarInjector`](src/main/java/dev/kubeui/plugin/KubeUISidebarInjector.java). Icons are
positioned along the inventory panel's actual `getLeftPos()`/`getTopPos()`, so they stay attached
to it regardless of window size.

## Debugging

Four client-side commands (`net.neoforged.neoforge.client.event.RegisterClientCommandsEvent`,
same mechanism KubeJS uses for `/kubejs`):

| Command | Effect |
|---|---|
| `/kubeui debug` | Prints a summary of the most recently opened KubeUI screen (title, tab/drag/resize/animated flags, per-type widget counts, every registered id). Reports on the *last* screen, not the currently-open one - typing a command closes whatever screen was open first. |
| `/kubeui scale [factor]` | With no argument, reports the current [scale](#responsive-sizing--scale). With one (`0.5`-`2.0`), sets it - lets a *player* shrink/grow every KubeUI screen themselves, without needing script support. Already-open screens need to be closed and reopened to pick up a new scale. |
| `/kubeui outline` | Toggles a magenta bounding-box outline around every visible widget on KubeUI screens, for debugging layout. |
| `/kubeui screenshot` | Takes a screenshot via the same mechanism as F2. |

## Extending KubeUI from Java

Third-party **Java** mods (not scripts) can register their own widget types:

```java
KubeUIWidgets.register("mymod:cool_widget", ctx -> new MyCoolWidget(ctx.width, ctx.height));
```

Scripts then use it like any built-in widget: `.custom("mymod:cool_widget", ...args)`. See
[`KubeUIWidgetFactory`](src/main/java/dev/kubeui/gui/KubeUIWidgetFactory.java).

## Project layout

- `src/main/java/dev/kubeui/KubeUI.java` - mod entry point (`@Mod`).
- `src/main/java/dev/kubeui/plugin/KubeUIPlugin.java` - `KubeJSPlugin` implementation,
  registered via `src/main/resources/kubejs.plugins.txt`, exposes the `KubeUI` global
  binding to client scripts.
- `src/main/java/dev/kubeui/gui/` - the actual screen/widget implementation:
  - `KubeUIScreenBuilder` - the script-facing spec (`KubeUI.builder(...)`).
  - `KubeUIScreen` - lays elements out, renders them, and owns the id -> widget maps.
  - `KubeUIContext` - the `screen` handle passed to callbacks.
  - `KubeUISlider`, `KubeUIProgressBar`, `KubeUIDivider`, `KubeUIImageWidget`, `KubeUIItemWidget`,
    `KubeUIColorSwatch` - small custom `AbstractWidget`/`AbstractSliderButton` subclasses for the
    widgets vanilla Minecraft doesn't already provide.
  - `KubeUIListItemRenderer` - the functional interface behind `.list(id, items, renderer)`.
  - `KubeUIResizeHandle`, `KubeUISpacer` - two more small custom widgets, for `.resizable(...)`
    and `.spacer(...)`.
  - `KubeUINarratable` - implemented by the custom widgets above to support `.narration(...)`.
  - `KubeUILayoutMath` - pure (Minecraft-free) helpers, unit-tested directly (see below).
  - `KubeUITheme` - the global colors behind `KubeUI.setTheme(...)`.
  - `KubeUIWidgets`, `KubeUIWidgetFactory`, `KubeUIWidgetFactoryContext` - the registry behind
    `.custom(type, ...)`, for **Java** mods (see [Behavior & polish](#behavior--polish)).
  - `KubeUIEvents`, `KubeUIScreenEvent` - the `KubeJSPlugin.registerEvents` group behind
    `KubeUIEvents.screenOpen`/`.screenClose` (see [Server integration](#server-integration)).
  - `KubeUIActionPayload`, `KubeUIActionHandler`, `KubeUIActions`, `KubeUINetworking` - the
    client->server networking layer behind `screen.runServerAction(...)`.
  - `KubeUIDebug` - backing state for the `/kubeui debug`/`/kubeui outline` commands (see
    [Debugging](#debugging)).
  - `KubeUISidebar`, `KubeUISidebarIcon`, `KubeUISidebarWidget` - the registry, data holder and
    render widget behind the survival-inventory icon bar (see [Sidebar](#sidebar-survival-inventory-icon-bar)).
- `src/main/java/dev/kubeui/plugin/KubeUIServerPlugin.java` - a second, minimal `KubeJSPlugin`
  that loads on **both** client and dedicated server (unlike `KubeUIPlugin`, which is
  client-only) - exposes `KubeUIActions` to `server_scripts`. Keeping this separate is what lets
  `KubeUIPlugin` reference `net.minecraft.client.*` classes without crashing a dedicated server.
- `src/main/java/dev/kubeui/plugin/KubeUIDebugCommands.java` - registers the `/kubeui debug`,
  `/kubeui outline`, `/kubeui screenshot` and `/kubeui scale` client commands.
- `src/main/java/dev/kubeui/plugin/KubeUISidebarInjector.java` - adds `KubeUISidebar`'s icons to
  the vanilla `InventoryScreen` via `ScreenEvent.Init.Post`, no mixin needed.

## Building

Requires a **full JDK 25** (not just a JRE - `javac` must be present). If `JAVA_HOME`
doesn't point at one, install it (e.g. `dnf install java-25-openjdk-devel`, or use
[Eclipse Temurin](https://adoptium.net/)) and export `JAVA_HOME` before building.

```bash
./gradlew build
```

The mod jar is produced at `build/libs/kubeui-<version>.jar`.

## Running in a dev environment

```bash
./gradlew runClient
```

KubeJS is a `compileOnly` dependency (it's expected to be provided by the runtime, not
bundled), so `runClient`/`runServer`/`runData` first run two tasks automatically:

- `downloadKubeJS` - resolves `dev.latvian.mods:kubejs-neoforge:26.1.2-8.0.4` and drops it
  into `run/mods/`, so KubeJS is actually loaded in the dev environment.
- `syncTestScripts` - syncs [`testkubejs/`](testkubejs/) into `run/kubejs/`.

### Testing the UI

[`testkubejs/`](testkubejs/) has a menu hub and four test screens - no keybinds involved, so it
works the same whether or not the client has focus grabbed by something else:

- [`client_scripts/kubeui_test_menu.js`](testkubejs/client_scripts/kubeui_test_menu.js) - opens
  automatically on joining a world (`ClientEvents.loggedIn`) and lists a button per demo below.
  This is the only script with an auto-open trigger; the other four just define their
  `openXxx()` function and wait to be called from here (or from `/kubejs run openXxx()`).
- [`client_scripts/kubeui_test.js`](testkubejs/client_scripts/kubeui_test.js) - every widget
  (Phases 1-12) and layout feature (row/grid/scrollPanel/list/anchor, Phases 13-20).
- [`client_scripts/kubeui_test_ux.js`](testkubejs/client_scripts/kubeui_test_ux.js) - tabs,
  draggable/resizable windows, tooltips, enable/visible toggling, add/remove after open, and
  confirm/alert dialogs (Phases 21-30).
- [`client_scripts/kubeui_test_shop.js`](testkubejs/client_scripts/kubeui_test_shop.js) - an item
  shop: clickable item icons (tooltip + click sound), a scrollable list built from a plain JS
  array (`SHOP_ITEMS`), a gold counter kept live via `.bind()`, a toggle that survives reopening
  the screen (`persistKey`), a real `KubeUI.alert(...)` error when a purchase costs more gold
  than you have, `screen.giveItem(...)` on a successful purchase, and a second
  "server-validated" purchase flow that round-trips through
  [`server_scripts/kubeui_actions.js`](testkubejs/server_scripts/kubeui_actions.js) so the price
  is never trusted from the client (see [Server integration](#server-integration)). This one is a
  demo of the *widgets* - the economy itself is a JS variable, fine for showing off the GUI but
  not how a real server would do it.
- [`client_scripts/kubeui_shop_real.js`](testkubejs/client_scripts/kubeui_shop_real.js) +
  [`server_scripts/kubeui_shop_real.js`](testkubejs/server_scripts/kubeui_shop_real.js) - the
  same idea done the way a real server would: right-click an Emerald Block to open it (no menu
  shortcut needed, though the menu also has one for convenience), pay with actual emeralds out
  of your inventory, and every check (price, affordability, inventory space) happens
  server-side. See [Server integration](#server-integration) for the details.
- [`client_scripts/kubeui_test_sizes.js`](testkubejs/client_scripts/kubeui_test_sizes.js) - a
  sizing playground: `elementSize()` swapped mid-screen (tiny/default/huge), per-element
  `width()`/`height()` overrides in the same row, extreme progress bar shapes, and a custom-font
  (`.font(...)`) screen.
- [`client_scripts/kubeui_test_responsive.js`](testkubejs/client_scripts/kubeui_test_responsive.js) -
  `.width("50%")`/`.width("45%")` rows that stay proportional as you resize the window, buttons to
  try `KubeUI.setScale(...)` live, and 30 stacked labels with no `.resizable()` to show the
  automatic scroll safety net kicking in.
- [`client_scripts/kubeui_sidebar_demo.js`](testkubejs/client_scripts/kubeui_sidebar_demo.js) -
  registers a handful of `KubeUISidebar.addItem(...)` icons at script load; open the survival
  inventory (**E**) to see them on its left edge.

Edit these scripts directly to try out changes - `run/kubejs/` is re-synced (not merged) from
`testkubejs/` on every launch, so treat `testkubejs/` as the source of truth, not `run/kubejs/`.

## Versions

| Component  | Version               |
|------------|------------------------|
| Minecraft  | 26.1.2                 |
| NeoForge   | 26.1.2.84               |
| KubeJS     | 8.0.4 (`26.1.2-8.0.4`) |
| Java       | 25                      |

## License, versioning & contributing

[MIT](LICENSE). Versioning policy (what counts as a breaking vs. additive change, and how a new
Minecraft version is handled) is in [VERSIONING.md](VERSIONING.md). Contributing guidelines and
issue templates are in [CONTRIBUTING.md](CONTRIBUTING.md); support/questions go through
[SUPPORT.md](SUPPORT.md)'s channels. Releases are tagged (`vX.Y.Z`) and published to
for ideas deliberately deferred past it.
