package dev.kubeui.gui;

import java.util.LinkedHashMap;
import java.util.Map;

/// Script-facing registry for fully custom HUD elements (bars, labels) - exposed to KubeJS as the
/// client-only global `KubeUIHud`. Unlike [KubeUIScreenBuilder] (modal screens you open/close),
/// these render continuously during normal gameplay - the real gap this closes: every HUD overlay
/// in this mod (boss bar, combat log, waypoint tracker, ...) used to be hardcoded Java with zero
/// script control over its look - "on peut créer nos propres GUI et UI en JS" for those too (real
/// ask) - a script can now build its own boss-bar-shaped (or any other bar-shaped) HUD element
/// purely from data, rendered by [dev.kubeui.plugin.KubeUIHudRenderer].
///
/// Example (KubeJS client script):
/// ```js
/// KubeUIHud.setBar('dragonHealth', {
///     anchor: 'topCenter', x: 0, y: 10, width: 180, height: 8,
///     value: 40, max: 100, barColor: 0xFFDD2020, label: 'Ender Dragon'
/// })
/// KubeUIHud.removeBar('dragonHealth')
/// ```
public final class KubeUIHud {
	/// `x`/`y` are pixel offsets from `anchor` (`"topLeft"` (default) / `"topCenter"` / `"topRight"`
	/// / `"bottomLeft"` / `"bottomCenter"` / `"bottomRight"`), resolved fresh every frame against
	/// the real current screen size - stays correctly positioned across any window size/GUI scale
	/// rather than needing a script to compute absolute coordinates itself.
	public record Bar(
		String id, String anchor, int x, int y, int width, int height, double value, double max,
		int barColor, int bgColor, int borderColor, String label, int labelColor
	) {
	}

	public record Label(String id, String anchor, int x, int y, String text, int color, boolean centered, boolean shadow) {
	}

	private static final Map<String, Bar> BARS = new LinkedHashMap<>();
	private static final Map<String, Label> LABELS = new LinkedHashMap<>();

	private KubeUIHud() {
	}

	/// Registers (or replaces/updates - just call again with the same `id`, e.g. every time health
	/// changes) a bar. `options` (any key may be omitted, sensible defaults fill the rest):
	/// `anchor` (`String`), `x`/`y`/`width`/`height` (`int`, defaults `0`/`0`/`180`/`8`), `value`/
	/// `max` (`double`, defaults `0`/`100`), `barColor`/`bgColor`/`borderColor`/`labelColor` (`int`
	/// ARGB, sensible defaults), `label` (`String`, default `""` - no label drawn if empty).
	public static void setBar(String id, Map<String, Object> options) {
		if (id == null) {
			return;
		}
		var o = options == null ? Map.<String, Object>of() : options;
		BARS.put(id, new Bar(
			id,
			str(o, "anchor", "topLeft"),
			intOf(o, "x", 0), intOf(o, "y", 0),
			intOf(o, "width", 180), intOf(o, "height", 8),
			doubleOf(o, "value", 0), doubleOf(o, "max", 100),
			intOf(o, "barColor", 0xFF3080E0), intOf(o, "bgColor", 0xFF202020), intOf(o, "borderColor", 0xFF000000),
			str(o, "label", ""), intOf(o, "labelColor", 0xFFFFFFFF)
		));
	}

	public static void removeBar(String id) {
		BARS.remove(id);
	}

	/// `options`: `anchor`/`x`/`y` (same as [#setBar]), `text` (`String`, required - a null/missing
	/// text removes the label instead of drawing an empty one), `color` (`int` ARGB, default
	/// white), `centered` (`boolean`, default `false` - `x`/`y` is the text's center instead of its
	/// top-left corner), `shadow` (`boolean`, default `true`).
	public static void setLabel(String id, Map<String, Object> options) {
		if (id == null) {
			return;
		}
		var o = options == null ? Map.<String, Object>of() : options;
		String text = str(o, "text", null);
		if (text == null) {
			LABELS.remove(id);
			return;
		}
		LABELS.put(id, new Label(
			id, str(o, "anchor", "topLeft"), intOf(o, "x", 0), intOf(o, "y", 0),
			text, intOf(o, "color", 0xFFFFFFFF), boolOf(o, "centered", false), boolOf(o, "shadow", true)
		));
	}

	public static void removeLabel(String id) {
		LABELS.remove(id);
	}

	/// Removes every bar and label this script (or any other) registered - for a script's own
	/// cleanup on reload rather than leaking stale entries under old ids forever.
	public static void clear() {
		BARS.clear();
		LABELS.clear();
	}

	/// Cross-package (called from [dev.kubeui.plugin.KubeUIHudRenderer], not a script-facing method
	/// itself - `Bar`/`Label` records are already `public` for that same reason).
	public static Iterable<Bar> bars() {
		return BARS.values();
	}

	public static Iterable<Label> labels() {
		return LABELS.values();
	}

	private static String str(Map<String, Object> o, String key, String fallback) {
		return o.get(key) instanceof String s ? s : fallback;
	}

	private static int intOf(Map<String, Object> o, String key, int fallback) {
		return o.get(key) instanceof Number n ? n.intValue() : fallback;
	}

	private static double doubleOf(Map<String, Object> o, String key, double fallback) {
		return o.get(key) instanceof Number n ? n.doubleValue() : fallback;
	}

	private static boolean boolOf(Map<String, Object> o, String key, boolean fallback) {
		return o.get(key) instanceof Boolean b ? b : fallback;
	}
}
