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

**If KubeJS ever ships Fabric again alongside NeoForge on the same Minecraft version**, the
hardest parts of KubeUI to port would be, roughly in order: the real NeoForge networking layer
(`CustomPacketPayload`/`PayloadRegistrar`/`PacketDistributor` - Fabric's networking API is shaped
differently and would need a real rewrite, not a thin wrapper); the NeoForge `PermissionAPI`
integration (`.requirePermission(...)`, `PermissionDynamicContextKey` - Fabric has no equivalent
built-in, a Fabric build would need to either depend on a permissions mod directly or drop the
feature); and `ModConfigSpec`-based config (`KubeUIConfig` - Fabric has no built-in config spec
API, config would need a different library or hand-rolled TOML/JSON). Widget/layout code itself
(`KubeUIScreenBuilder`/`KubeUIScreen`, built on vanilla `Screen`/`GuiGraphics`/layout classes) is
comparatively loader-agnostic already, since those are Mojang classes both loaders wrap rather than
replace. This is a written analysis for if the question ever comes up again, not a plan currently
being acted on - see "Why NeoForge only?" above for why a real multi-loader build isn't happening
right now regardless of how hard porting would be.

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

### Sharing reusable widget compositions

There's no special "component" API for a reusable chunk of layout - a plain JS function that takes
a builder and adds to it already works, since every `.xyz(...)` call returns the same builder to
keep chaining:

```js
// A reusable piece, shareable as just this function (copy it into another script, or `require`/
// import it if your setup supports JS modules) - not a KubeUI-specific format.
function addHealthBar(b, current, max) {
    return b
        .label('hpLabel', 'HP: ' + current + ' / ' + max)
        .progressBar('hpBar', current, max)
}

KubeUI.builder('Status')
    .when(true, b => addHealthBar(b, 8, 20))
    .button('Close', screen => screen.close())
    .open()
```

This composes with `.when(...)`/`.repeat(...)` (see
[Introspecting a screen from script code](#introspecting-a-screen-from-script-code)) the same way
any other builder call does. There's no registry/marketplace of published compositions built into
KubeUI itself - see [CONTRIBUTING.md](CONTRIBUTING.md) if you want to share one; for now that's a
plain gist/repo/Discussion post, not a dedicated format this project maintains.

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
| `KubeUI.setTheme(titleColor, accentColor, textColor)` / `KubeUI.setTheme(name)` / `KubeUI.resetTheme()` | Global colors (ARGB `long`s, or a named preset - `"default"`/`"dark"`/`"light"`/`"high-contrast"`/one registered via `KubeUI.registerThemePreset(name, ...)`) applied to screens built from now on - only affects what KubeUI draws directly (title, progress bar fill); vanilla widgets keep their own sprites. Changing it while a screen is open fades over 300ms. See [Accessibility](#accessibility). |
| `.style({color, accent})` | Overrides the color(s) the last-added element draws with, beyond the global theme - only has an effect on the handful of elements that already read theme colors directly (see [Accessibility](#accessibility)). |
| `KubeUI.setFontScale(factor)` / `KubeUI.resetFontScale()` | Multiplies the size of *text only* (not box sizes) for KubeUI's own custom-drawn text, independent of `KubeUI.setScale(...)`. See [Accessibility](#accessibility). |

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
| `KubeUISidebar.setIconPack(overrides)` / `.clearIconPack()` | Overrides the rendered icon (a texture) for every id present in `overrides`, regardless of how it was originally registered - for reskinning the whole bar in one call to match a resource pack, without every addon that registered an icon needing to cooperate. |

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

Client-side commands (`net.neoforged.neoforge.client.event.RegisterClientCommandsEvent`,
same mechanism KubeJS uses for `/kubejs`):

| Command | Effect |
|---|---|
| `/kubeui debug` | Prints a summary of the most recently opened KubeUI screen (title, tab/drag/resize/animated flags, per-type widget counts, every registered id). Reports on the *last* screen, not the currently-open one - typing a command closes whatever screen was open first. |
| `/kubeui scale [factor]` | With no argument, reports the current [scale](#responsive-sizing--scale). With one (`0.5`-`2.0`), sets it - lets a *player* shrink/grow every KubeUI screen themselves, without needing script support. Already-open screens need to be closed and reopened to pick up a new scale. |
| `/kubeui fontscale [factor]` | Same as `/kubeui scale`, but text-only - see [`setFontScale`](#accessibility). |
| `/kubeui theme preview <name>` | Applies a named [theme preset](#accessibility) for a few seconds, then reverts automatically - try one before committing to it with a script. |
| `/kubeui outline` | Toggles a magenta bounding-box outline around every visible widget on KubeUI screens, for debugging layout. |
| `/kubeui grid` | Toggles a layout debug grid with pixel dimensions, on top of the outline. |
| `/kubeui screenshot` | Takes a screenshot via the same mechanism as F2. |
| `/kubeui export` / `/kubeui import` | Moves a player's KubeUI preferences (scale/font scale/theme defaults, window positions) to/from a portable file - see [`KubeUIPreferences`](src/main/java/dev/kubeui/gui/KubeUIPreferences.java). |
| `/kubeui reload-config` | Reports the config values currently in effect (`config/kubeui-common.toml` auto-reloads on external edits already - this doesn't trigger anything, just confirms an edit took). |
| `/kubeui editor` | Opens an in-game file manager/editor for `kubejs/client_scripts` - list, create, open, edit and delete `.js` files without leaving the game, "Save & Reload" to see a change live. |
| `/kubeui profile` | Reports how long the most recently built KubeUI screen took to construct, and how many widgets it has. |
| `/kubeui stresstest` | Builds and opens 30 synthetic 40-widget screens back to back, reports total/average build time - an on-demand load test. |

### Introspecting a screen from script code

- `.when(condition, b => b.button(...))` - runs the callback against this same builder only if
  `condition` is true, an alternative to breaking a fluent chain with a bare `if`.
- `.repeat(count, (b, i) => ...)` - calls the callback `count` times; a lighter [`.list(...)`](#layout)
  when there's no real array to iterate, just a repeated shape.
- `KubeUI.describe(builder)` - a human-readable, indented listing of every element's type and id in
  `builder`, for debugging or generating docs from a screen instead of writing them by hand.
- `KubeUI.lint(builder)` - returns a list of issues detectable from the already-built entry tree
  (today: duplicate element ids) - callable before `.open()` is ever reached.
- `KubeUI.toJson(builder)` / `KubeUI.fromJson(json)` - JSON export/import of a screen's *layout*,
  covering a representative widget subset (label, button, toggle, textField, slider, number,
  divider, spacer, row, grid) rather than the full catalog. Callbacks can't survive a round trip
  through JSON (they're JS closures, not data) - `fromJson` rebuilds every interactive element with
  a no-op callback for the script to fill back in.
- `screen.dumpTree()` - a JSON snapshot of a currently-open screen's widget tree (same format/scope
  as `toJson`), meant for saving two snapshots and diffing them rather than feeding back into
  `fromJson`.

## Accessibility

What's covered, and its known limits - so a scripter knows what they can rely on without having to
read the source to find out.

**Covered:**

- **Narration (screen readers).** Every interactive vanilla widget (button, toggle, text field,
  dropdown, ...) narrates its own label/value automatically - that's Minecraft's own behavior, not
  something KubeUI adds. Every KubeUI-custom-drawn widget (rating, badge, table, chart, minimap,
  range slider, keybind capture, ...) implements its own narration too, overridable per-element via
  `.narration(text)` / `.narrationKey(langKey, fallbackText)`.
- **Keyboard navigation.** Tab cycles focus through every widget on a screen (wraps around both
  ways - vanilla's own Tab handling doesn't, KubeUI patches it); `.tabOrder(n)` sets an explicit
  group where the default (visual-position-based for absolute-positioned elements, declared order
  otherwise) isn't what's wanted. Every custom KubeUI widget that's actually interactive - rating,
  range slider, keybind capture, list-select checkbox, table, the drag-and-drop/context-menu
  system - is fully operable from the keyboard alone (arrow keys / Enter / Space), not just the
  mouse, and draws a visible focus outline so a keyboard user can see where they are.
- **Color & contrast.** `KubeUI.setTheme("high-contrast")` (or `"dark"`/`"light"`/a custom preset
  via `KubeUI.registerThemePreset(...)`) recolors everything KubeUI draws directly; changing it
  mid-session fades rather than snaps. `.colorPicker(...)`'s default swatch palette is the Okabe &
  Ito colorblind-safe set, not arbitrary saturated hues. `.style({color, accent})` overrides a
  single element beyond the theme.
- **Text size.** `KubeUI.setFontScale(factor)` / `/kubeui fontscale` enlarges text independently of
  box sizes, for KubeUI's own custom-drawn text (`.richText()`, `.table()`, `.chart()`'s labels,
  `.rangeSlider()`, `.keybindCapture()`, `.progressBar()`'s percentage).
- **Sound captions.** `.sound(soundId)` plays through the real vanilla `SoundManager`, so
  Minecraft's own "Show Subtitles" option already captions it automatically - no KubeUI-side code
  needed - *provided* `soundId` resolves to a sound event that actually has a `subtitle` key in a
  loaded `sounds.json` (true for vanilla/registered sounds; an arbitrary made-up id won't caption,
  the same way it wouldn't in vanilla either).
- **Translated text.** Beyond `.labelKey(id, langKey, fallbackText)`, every other single-string
  builder method that takes literal text has a `Key`-suffixed twin resolved from the language file
  the same way: `.buttonKey`, `.toggleKey`, `.textFieldKey`/`.textAreaKey` (hint only - field
  *content* is real data, not translated), `.tooltipKey`, `.narrationKey`, `.badgeKey`.

**Known limits:**

- **No right-to-left layout.** Minecraft's own `GridLayout`/text rendering have no mirroring
  support to build on - a widget tree built left-to-right can't be flipped without a custom layout
  engine, which is out of scope.
- **No colorblind simulation mode.** A real (not approximated) simulation needs a GPU
  post-processing pass (like vanilla's own nausea/creeper-vision effects), which nothing in KubeUI
  currently sets up - `/kubeui theme preview <name>` (via `"high-contrast"`) is the closest
  built-in tool for checking a screen's colors.
- **Vanilla widgets aren't recolorable/rescalable from outside.** `.style(...)`,
  `KubeUI.setTheme(...)` and `KubeUI.setFontScale(...)` only affect what KubeUI draws directly -
  a `.button()`/`.toggle()`/`.textField()`/dropdown's own text/sprites are Minecraft's, not
  KubeUI's, to recolor or rescale (the same limitation `.narration(...)` already documents for
  those widget types).

## Extending KubeUI from Java

Third-party **Java** mods (not scripts) can register their own widget types:

```java
KubeUIWidgets.register("mymod:cool_widget", ctx -> new MyCoolWidget(ctx.width, ctx.height));
```

Scripts then use it like any built-in widget: `.custom("mymod:cool_widget", ...args)`. See
[`KubeUIWidgetFactory`](src/main/java/dev/kubeui/gui/KubeUIWidgetFactory.java).

## Compatibility

**Other KubeJS-UI addons.** Every global KubeUI binds (`KubeUI`, `KubeUISidebar`,
`KubeUIScreenInjector`, `KubeUIRemoteScreens`) is namespaced under the `KubeUI` prefix specifically
to stay out of the way of another addon's own globals - two addons only collide if they both choose
the exact same global name, which a `KubeUI`-prefixed one is unlikely to. Widget/element ids are
scoped per-screen (a builder's own `entries`), not shared mod-wide, so two different mods' screens
can reuse the same id strings freely. `.custom(...)` widget type names (see above) follow the same
`modid:name` convention as everything else Minecraft/NeoForge namespaces, so two mods registering
custom widgets can't collide either as long as each uses its own mod id as the prefix.

**Config screen.** KubeUI registers a real NeoForge `IConfigScreenFactory`, so the "Config" button
next to KubeUI in the vanilla Mods list opens a working settings screen - no separate mod (e.g. a
Fabric-only "mod menu"-style addon, which wouldn't apply to a NeoForge-only project) is involved.

**JEI/REI, resource packs, shader packs, other content mods.** Not validated against real
installs of any of these in this project's own dev/CI environment (none are dependencies here) -
KubeUI doesn't intercept input in a way that should conflict with an item-browsing overlay (Esc
closes a KubeUI screen normally, no aggressive global scissor/rendering hijack), and its custom
widgets render through the same `Identifier`-keyed textures/vanilla text rendering a resource pack
or shader pack already knows how to handle - but neither claim has been exercised against a real
install. Genuinely testing this needs an environment with those mods actually present.

| Tested with | Status | Notes |
|---|---|---|
| KubeJS (required dependency) | ✅ Works | The whole point - see [Versions](#versions) for the exact tested version. |
| JEI / REI | ❔ Not validated | Not installed in this project's dev/CI environment - see above. |
| Other KubeJS-UI addons | ❔ Not validated | No known collisions by design (see above), not exercised against a real second addon. |
| Resource packs | ❔ Not validated | Widgets use real `Identifier` textures, should be overridable in principle - not confirmed against an actual pack. |
| Shader packs (Iris/OptiFine-like) | ❔ Not validated | No custom shaders/post-processing of its own that would obviously conflict - not confirmed against a real shader pack. |

A dedicated compatibility page separate from this README isn't warranted yet - this table is
short enough to live here until it has more than a handful of genuinely-tested rows.

## Community & ecosystem

- **Discussion/feedback:** [GitHub Discussions](https://github.com/Zynora-fr/KubeUI/discussions) -
  the **Ideas** category (with reactions already enabled by GitHub by default) is the place to
  react/comment on a roadmap idea instead of opening an issue for it; see
  [SUPPORT.md](SUPPORT.md) for the rest.
- **"Made with KubeUI" badge:** if your project uses KubeUI, you're welcome to link back to it from
  your own Modrinth page/README with a plain badge - there's no official image asset for this yet,
  a simple `[Made with KubeUI](https://github.com/Zynora-fr/KubeUI)` link or a
  [shields.io](https://shields.io) badge (e.g. `https://img.shields.io/badge/Made%20with-KubeUI-blue`)
  both work fine; nothing to register or ask permission for.
- **Community showcase / example registry:** not built yet - there isn't a real body of published
  third-party KubeUI scripts/projects to list honestly, so a showcase page or example registry
  would be an empty shell right now. Worth revisiting once that changes.
- **Starter template:** [`templates/starter/`](templates/starter/) is a minimal, working
  `client_scripts`/`server_scripts` layout to clone/copy as a new KubeUI project's starting point,
  or generate with `node scripts/create-kubeui-script.js <target-dir>` - see its own README.
- **Step-by-step tutorial:** [`TUTORIAL.md`](TUTORIAL.md) walks through building a complete screen
  from nothing, for a scripter who's never touched KubeUI before.
- **Advanced reference example:** [`testkubejs/server_scripts/kubeui_quest_board_example.js`](testkubejs/server_scripts/kubeui_quest_board_example.js)
  (+ its client half) is a small but complete server-authoritative system (a quest board with
  real state, not just a UI mockup) built with KubeUI, meant to be read as a bigger worked example
  than the targeted `testkubejs/` demo scripts.

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
  and layout feature (row/grid/scrollPanel/list/anchor).
- [`client_scripts/kubeui_test_ux.js`](testkubejs/client_scripts/kubeui_test_ux.js) - tabs,
  draggable/resizable windows, tooltips, enable/visible toggling, add/remove after open, and
  confirm/alert dialogs.
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
[SUPPORT.md](SUPPORT.md)'s channels. Releases are tagged (`vX.Y.Z`) and published to GitHub
Releases, Modrinth, and CurseForge automatically (see
[`release.yml`](.github/workflows/release.yml)). [CHANGELOG.md](CHANGELOG.md) has what's actually
shipped release to release.
