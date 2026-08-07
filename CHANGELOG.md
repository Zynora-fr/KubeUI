# Changelog

## 0.3.0-1.21.1

Port of 0.3.0 to NeoForge 1.21.1 (KubeJS 2101.7.2-build.368) - same feature set as 0.3.0 below,
rebuilt against 1.21.1's own rendering, input, scrolling, NBT, permissions, networking, recipe and
container-menu APIs. No functional or content changes versus 0.3.0.

## 0.3.0

**Theming & accessibility:** named theme presets (`"dark"`/`"light"`/`"high-contrast"`, or a custom
one via `KubeUI.registerThemePreset(...)`) that fade between each other instead of snapping; a
colorblind-safe default `.colorPicker()` palette; `.style({color, accent})` to override a single
element's colors beyond the global theme; `KubeUI.setFontScale(...)` to enlarge text independently
of box sizes; `KubeUISidebar.setIconPack(...)` to reskin the whole sidebar in one call;
`/kubeui theme preview <name>`; `Key`-suffixed translated variants of every text-bearing widget
(`.buttonKey()`, `.toggleKey()`, `.textFieldKey()`, `.textAreaKey()`, `.tooltipKey()`,
`.narrationKey()`, `.badgeKey()`); and a visible keyboard focus outline plus full keyboard
operability on every custom widget that was missing one (range slider, keybind capture field,
context menus).

**Update checking:** the player is told in chat, on joining a world, whether they're running the
latest KubeUI version - via NeoForge's own built-in update checker, sourced from the real
CurseForge project (no custom network code, no API key).

**Server networking:** the server can now push a screen to a specific player or broadcast one to
everyone (`KubeUIActions.openRemote(...)`/`.broadcastUpdate(...)`, received via
`KubeUIRemoteScreens.register(...)`); `.requirePermission(gate)` gates a widget behind NeoForge's
real permission API (LuckPerms or equivalent can plug in); `.screenId(id)` lets the server know
which screen a player currently has open (`KubeUIActions.getOpenScreenId(...)`); actions can be
throttled per player (`KubeUIActions.register(id, throttleMs, handler)`) and schema-validated
before they ever reach a handler; `screen.runServerAction(..., onAck)` confirms an action actually
ran; and the server can show/hide a sidebar icon per player.

**Config & persistence:** KubeUI now has a real disk-backed config (`config/kubeui-common.toml`) -
the personal scale/font scale/theme a player sets are remembered across restarts by default;
`.draggable()` window positions persist across game launches, not just within a session;
`/kubeui export`/`/kubeui import` moves a player's KubeUI preferences between installs;
`KubeUIActions.playerData(player)` gives a script real per-player server-side storage with no
setup; and `KubeUI.configScreen(schema)` builds a settings screen straight from a data description
instead of a hand-chained builder.

**In-game script editor:** `/kubeui editor` opens a real file manager/text editor for
`kubejs/client_scripts` without leaving the game - list, create, open, edit and delete any `.js`
file, then "Save & Reload" to see the change live immediately (the same reload behind `/kubejs
reload client-scripts`). Replaces two earlier attempts (a click-to-build visual editor that didn't
hold up in real play, then a single fixed canvas file meant to be edited externally) with actual
in-game text editing.

**Developer tooling:** `.when(condition, b => ...)`/`.repeat(count, (b, i) => ...)` builder helpers;
`KubeUI.describe(builder)` for a human-readable widget/id listing; `KubeUI.lint(builder)` to catch
duplicate ids before a screen ever opens; `KubeUI.toJson(builder)`/`.fromJson(json)` and
`screen.dumpTree()` for a JSON snapshot of a screen's layout (a representative widget subset);
error/warning messages now name the widget type and id involved; a slow script callback (50ms+) is
now logged instead of just silently making the game stutter; `/kubeui profile` and
`/kubeui stresstest` for build-time timing; and a real NeoForge "Config" button next to KubeUI in
the Mods list (not the Fabric-only ModMenu the idea was originally framed around).

**Robustness:** a runaway recursive layout helper now fails with a clear error instead of a
`StackOverflowError`; `.draggable()`/persisted widget state and pending server-action
acknowledgements are now capped instead of growing unboundedly for a long-running client or a
server that never replies; and a player's per-action throttle state is now actually cleared on
disconnect (a real gap a security-focused audit found alongside the above).

**Docs & community:** a [`TUTORIAL.md`](TUTORIAL.md) walkthrough from the first `KubeUI.builder(...)`
to a complete server-backed screen; a [`templates/starter/`](templates/starter/) starter
project (generatable with `node scripts/create-kubeui-script.js <dir>`); a bigger worked example
(a quest board with real per-player server-side progress, `testkubejs/*/kubeui_quest_board_example.js`);
and a [`SECURITY.md`](SECURITY.md) covering the project's security policy.

**Recipes:** `KubeUI.recipeScreen(recipeTypeId)` opens a screen showing every recipe of a given
type (vanilla or a script's own custom one) automatically, and `KubeUI.recipesFor(itemId, onResult)`
looks up every recipe that accepts a given item as an ingredient - both work generically across
recipe types, real server-side data (not guessed or client-only), shown in a properly-sized,
scrollable screen. `.recipeSlot(id, itemIds, onClick)` is a new JEI-style widget that cycles
through a group of acceptable items instead of showing just one.

**Custom recipes:** `/kubeui recipe-designer` opens an in-game screen to define your own
crafting/furnace/blast-furnace/smoker/stonecutter/smithing-table recipes - pick a kind, then
arrange real items (any item from your own inventory, dragged in like the real thing) in that
kind's actual vanilla interface, and left-click the result slot to save. Saving/deleting doesn't
reload automatically anymore (a full data-pack reload on every click was a real lag spike) - a
"Reload Recipes" button in the designer applies everything you've changed in one go.

**Custom trading:** `/kubeui trader-designer` opens an in-game screen to build a custom trader
entirely without scripts - add trades in a real item-slot GUI (place the cost items and the result,
left-click the result to add it), toggle whether it has AI and whether it can move, then give
yourself the finished trader as a real item (using the actual villager spawn egg icon) and
right-click a block to spawn it, exactly like a vanilla spawn egg. Trading with it uses the real
villager trading screen too, laid out just like a real villager with a profession - a trader with
several trades shows them as a real list of cost/result icons (handy since one trader having
multiple trades is the common case), and picking a row pulls its cost items from your own inventory
into the payment slots automatically, same as clicking an offer on a real villager - left-click the
result to receive it and pay. A trader's trades are baked directly onto the spawned villager itself,
so they're still there after closing and reopening the game - not lost on a server restart like an
earlier build.
`KubeUIActions.registerTradePool(poolId, trades, condition?)` still defines a
weighted-random pool of trades from a script (with configurable stock/restocking and optional
reputation/quest gating) for anyone who wants that lower-level control, and
`KubeUIActions.tagTradePool(entity, poolId)` turns any entity into a trader that way instead.
`/kubeui villager-trades <target>` inspects an entity's current trades/stock either way, and
`KubeUIActions.tradeHistory(player)` reports what a player has already bought.

**Quests:** `KubeUIActions.defineQuest(id, { title, description, requires, objectives, rewards })`
defines a real, server-tracked quest - built-in objective types cover collecting items, killing a
kind of entity, visiting a position or structure, and reaching an XP level, and any other type is a
plain counter your own script bumps from whatever event it wants
(`KubeUIActions.incrementQuestObjective(...)`), no callback registration needed. `requires` chains
quests together (a quest only becomes available once its prerequisites are completed), and
progress lives on your own player data - it's still there after closing the game entirely.
`KubeUI.questLog()`/`/kubeui quest-log` (also reachable as the shorter `/quest`) shows every quest
and your progress on each, grouped by status; a small always-on-screen tracker (`Track` button in
the log) shows your current objective without needing the log open. `KubeUIActions.tagQuestGiver(entity, questIds)`/
`/kubeui tag-quest-giver` turns any entity into a quest giver - right-clicking it opens a real
Accept/Turn-in screen, server-verified on every click so nothing can be granted twice.
`/quest accept <questId>`/`/quest complete <questId>` do the same without needing a giver entity
at all, for a server that wants quests reachable purely by id.
`/kubeui quest-editor` composes a quest entirely in-game, no script required, and - unlike a
script, which re-declares its quests every boot - saves it for real so it survives a server
restart too.

**Look & feel:** `KubeUIScreenBuilder.windowBackground(texture)` gives a screen a real nine-slice
panel drawn behind its whole content (not just a decorative strip) - the quest screens, recipe
designer, trader designer, script editor, and recipe browser each now have their own real,
custom-drawn panel texture instead of the plain default look: a dark panel with rounded corners
and a soft glowing accent border, each screen's own accent color, sharing one consistent style so
it's obvious at a glance which screen is open. The custom trader's trade/payment screens no longer
reuse vanilla's villager trading texture either - they're now the same custom-drawn panel style,
with real hand-drawn slot frames instead of texture-baked ones.

**Fixes:** a script's `runServerAction` handler is now actually wrapped in a try/catch server-side
(a prior gap where a bug in one could take down the packet-handling thread); the quest board
reference example now actually takes the required items from the player's inventory on completion
instead of only checking a flag; a custom smelting/blasting/smoking recipe from the recipe designer
now actually clears any existing recipe for the same input first, so it can't be silently shadowed
by an existing one (e.g. a custom log recipe losing out to vanilla's own log-to-charcoal recipe);
the trader designer's trade list and the recipe browser's ingredient arrow were sized wrong (a
label with no explicit width silently claims the whole panel's width) and could overflow well past
the screen on some window sizes - every built-in screen has been re-audited for the same mistake;
a custom trader's trade-offer list built its menu slots in the wrong order internally, which could
select the wrong offer when clicking a row in the list (not just a display glitch - the payment
slots that filled in could genuinely be for a different trade than the one clicked).

Full documentation: [github.com/Zynora-fr/KubeUI](https://github.com/Zynora-fr/KubeUI#readme).

Requires KubeJS on NeoForge - see the README's Versions table for the exact
Minecraft/NeoForge/KubeJS versions this build targets.

## 0.2.0

**New widgets:** rich text, status badges, star ratings, loading spinners, nine-slice panel
backgrounds, live entity/block previews, rebindable keybind fields, sortable data tables,
collapsible trees, bar/line charts, a Journeymap-style minimap always centered on the player,
reorderable/selectable/grouped/paginated list variants, split panes, accordions, breadcrumbs,
multi-step wizards, range sliders, stepped sliders, date pickers, searchable and multi-select
dropdowns, a debounced search box, and a resource picker.

**Layout:** `grow` (flexbox-style leftover space distribution in rows), `absolute`/`zIndex`
positioning outside the normal layout flow, responsive width breakpoints
(`.width({default, small, tiny})`) beyond plain percentages, and split panes.

**Rendering:** a real GPU `renderScale` (unlike the pixel-size `setScale`, this scales the fully
laid-out screen including text, and corrects mouse input to match), selectable screen backgrounds
(`"dirt"`/`"blur"`/`"none"`/a custom texture), and slide/scale animation types with an
`easeInOut` easing option alongside the existing fade.

**Windows:** edge-snapping while dragging, minimizable title bars, and non-modal windows so
several independent KubeUI screens can be shown at once instead of the usual one-at-a-time
replacement.

**Interaction:** generic drag-and-drop between any two widgets (not just list reordering),
per-screen hotkeys, static and dynamically-computed right-click context menus, undoable text
fields (Ctrl+Z/Ctrl+Y), double-click actions, delayed hover-preview popovers, momentum scrolling,
`screen.shake()` for refused actions, and cross-fading between two screens
(`screen.transitionTo(...)`).

**Also:** `KubeUI.toast(...)` for non-modal notifications, `KubeUI.registerCommand(...)` to open a
screen from a plain `/command`, and `KubeUI.reserveSafeArea(...)`/`.clearSafeArea(...)` for
coexisting with another mod's screen-edge HUD.

**Fixes:** list drag-to-reorder now moves rows live instead of only reordering after the drag
ends, scroll position no longer resets mid-drag, and the minimap no longer lags and stays properly
centered on the player as they move.

Full documentation: [github.com/Zynora-fr/KubeUI](https://github.com/Zynora-fr/KubeUI#readme).

Requires KubeJS on NeoForge - see the README's Versions table for the exact
Minecraft/NeoForge/KubeJS versions this build targets.

## 0.1.0 - Initial release

First public release. A KubeJS addon for building interactive, widget-based GUIs from
`client_scripts` - no Java required.

**Widgets:** buttons, labels, toggles, text fields/areas, sliders, dropdowns, radio groups,
checkbox groups, number spinners, color pickers, progress bars, item icons, images, dividers.

**Layout:** rows, grids, scroll panels (with an automatic fallback for screens taller than the
window), tabs, anchoring, percentage-based sizing (`.width("50%")`), per-element
width/height/padding/alignment.

**Screens & UX:** draggable and resizable windows, fade in/out animation, custom fonts, a global
color theme and a personal UI scale (`/kubeui scale`) independent of Minecraft's own GUI Scale,
confirm/alert dialogs, tooltips, tab order, narration for accessibility.

**Server integration:** `screen.runServerAction(id, data)` for building server-authoritative
features (the server decides everything - price, permissions, results - the client only ever
sends an id), plus a `KubeUIEvents` event group other scripts/addons can hook into.

**Sidebar:** `KubeUISidebar` - a Paladium-style icon bar next to the survival inventory screen,
for menus that should always be one click away.

**Debugging:** `/kubeui debug`, `/kubeui outline`, `/kubeui screenshot`.

**For Java mods:** `KubeUIWidgets.register(...)` to add custom widget types.

Full documentation: [github.com/Zynora-fr/KubeUI](https://github.com/Zynora-fr/KubeUI#readme).

Requires KubeJS on NeoForge - see the README's Versions table for the exact
Minecraft/NeoForge/KubeJS versions this build targets.
