package dev.kubeui.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BiConsumer;

/// A data table (`.table(id, columnLabels, columnWidths, rows)`): a header row plus one row per
/// entry in `rows`, alternating row background, columns aligned to `columnWidths`. Renders
/// entirely itself (not composed from `.row()`/`.label()`) so columns across every row line up
/// exactly and rows can carry a background fill - neither is easy to get pixel-exact out of
/// `GridLayout`. Cell text isn't clipped/wrapped to its column width - a value wider than its
/// column just overlaps the next one, same trade-off `.label()` already makes.
class KubeUITable extends AbstractWidget implements KubeUINarratable {
	private static final int ROW_HEIGHT = 14;
	private static final int HEADER_HEIGHT = 16;
	private static final int CELL_PADDING = 4;

	private final List<String> columnLabels;
	private final List<Integer> columnWidths;
	private final List<List<String>> rows;
	private final Font font;
	private final KubeUIContext context;
	private final BiConsumer<KubeUIContext, Integer> onSort;
	private final Integer styleColor;
	private final Integer styleAccent;
	private String narration;

	KubeUITable(int x, int y, int totalWidth, List<String> columnLabels, List<Integer> columnWidths, List<List<String>> rows, Font font, KubeUIContext context, BiConsumer<KubeUIContext, Integer> onSort, Integer styleColor, Integer styleAccent) {
		super(x, y, totalWidth, HEADER_HEIGHT + rows.size() * ROW_HEIGHT, Component.literal("Table"));
		this.columnLabels = columnLabels;
		this.columnWidths = columnWidths;
		this.rows = rows;
		this.font = font;
		this.context = context;
		this.onSort = onSort;
		this.styleColor = styleColor;
		this.styleAccent = styleAccent;
	}

	private int columnX(int column) {
		int x = getX();
		for (int i = 0; i < column; i++) {
			x += columnWidths.get(i);
		}
		return x;
	}

	private int columnAt(double mouseX) {
		int x = getX();
		for (int i = 0; i < columnWidths.size(); i++) {
			x += columnWidths.get(i);
			if (mouseX < x) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public void setCustomNarration(String text) {
		this.narration = text;
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		if (onSort != null && event.y() - getY() < HEADER_HEIGHT) {
			int column = columnAt(event.x());
			if (column >= 0) {
				onSort.accept(context, column);
			}
		}
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int accent = styleAccent != null ? styleAccent : KubeUITheme.accentColor();
		int text = styleColor != null ? styleColor : KubeUITheme.textColor();

		for (int c = 0; c < columnLabels.size(); c++) {
			int cx = columnX(c) + CELL_PADDING;
			int cy = getY() + 4;
			String label = columnLabels.get(c);
			KubeUIFontScale.draw(graphics, cx, cy, () -> graphics.text(font, label, cx, cy, accent, false));
		}

		int rowsTop = getY() + HEADER_HEIGHT;
		for (int r = 0; r < rows.size(); r++) {
			int rowY = rowsTop + r * ROW_HEIGHT;
			if (r % 2 == 1) {
				graphics.fill(getX(), rowY, getX() + getWidth(), rowY + ROW_HEIGHT, 0x22FFFFFF);
			}

			List<String> row = rows.get(r);
			for (int c = 0; c < row.size() && c < columnWidths.size(); c++) {
				int cx = columnX(c) + CELL_PADDING;
				int cy = rowY + 3;
				String cell = row.get(c);
				KubeUIFontScale.draw(graphics, cx, cy, () -> graphics.text(font, cell, cx, cy, text, false));
			}
		}

		KubeUIFocusOutline.draw(graphics, this);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		output.add(NarratedElementType.TITLE, Component.literal(narration != null ? narration : String.join(", ", columnLabels) + ", " + rows.size() + " rows"));
	}
}
