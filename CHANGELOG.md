# Changelog

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
