# Changelog

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
