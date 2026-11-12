package org.nasdanika.sdk.roadmap.runtime.widget.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.nasdanika.sdk.roadmap.runtime.widget.Badge;
import org.nasdanika.sdk.roadmap.runtime.widget.ItemList;
import org.nasdanika.sdk.roadmap.runtime.widget.KindFactory;
import org.nasdanika.sdk.roadmap.runtime.widget.KindProvider;
import org.nasdanika.sdk.roadmap.runtime.widget.Link;
import org.nasdanika.sdk.roadmap.runtime.widget.Section;
import org.nasdanika.sdk.roadmap.runtime.widget.Table;
import org.nasdanika.sdk.roadmap.runtime.widget.Text;
import org.nasdanika.sdk.roadmap.runtime.widget.Variant;
import org.nasdanika.sdk.roadmap.runtime.widget.Widget;

import io.opentelemetry.api.logs.Severity;

/**
 * The neutral kinds of a terminal, written against {@link TerminalContext} and nothing else, so
 * they serve every provider. They draw with ASCII only, so that what they draw is also good as plain
 * text, in any font; a provider with more to offer provides kinds of its own. A widget here gives back the rows it used, and widgets are stacked by
 * adding those up: there is no pass to measure before the pass to draw.
 */
public class TerminalKinds extends KindFactory {

	public TerminalKinds() {
		super(
			new KindProvider<>(Text.class, TerminalContext.class, new TerminalText()),
			new KindProvider<>(Link.class, TerminalContext.class, new TerminalLink()),
			new KindProvider<>(Section.class, TerminalContext.class, new TerminalSection()),
			new KindProvider<>(ItemList.class, TerminalContext.class, new TerminalItemList()),
			new KindProvider<>(Badge.class, TerminalContext.class, new TerminalBadge()),
			new KindProvider<>(Table.class, TerminalContext.class, new TerminalTable()));
	}

	/**
	 * Renders a widget and returns the rows it used: its result when that is a number, which is
	 * what terminal widgets give back, and one row for a widget that gives back something else.
	 */
	// tag::rows[]
	public static <C extends TerminalContext> int rows(Widget<?, ? super C> widget, C context) {
		if (context.getRows() <= 0 || context.getColumns() <= 0) {
			return 0;
		}
		return widget.render(context) instanceof Integer rows ? rows : 1;
	}
	// end::rows[]

	/**
	 * Breaks a line at spaces so that each piece fits the width. A word that is longer than the
	 * width is broken after a dot or a slash where it has one, as a qualified name or a path does,
	 * and in the middle where it has none.
	 */
	static List<String> wrap(String line, int width) {
		List<String> pieces = new ArrayList<>();
		String rest = line;
		while (rest.length() > width) {
			int at = rest.lastIndexOf(' ', width);
			if (at <= 0) {
				int separator = Math.max(rest.lastIndexOf('.', width - 1), rest.lastIndexOf('/', width - 1));
				int cut = separator > 0 ? separator + 1 : width;
				pieces.add(rest.substring(0, cut));
				rest = rest.substring(cut);
			} else {
				pieces.add(rest.substring(0, at));
				rest = rest.substring(at + 1);
			}
		}
		pieces.add(rest);
		return pieces;
	}

	private static final class TerminalText implements Text<TerminalContext> {

		@Override
		public Widget<Integer, TerminalContext> text(Supplier<String> value, String format) {
			return context -> {
				if (context.getColumns() <= 0) {
					return 0;
				}
				if (!PLAIN.equals(format)) {
					context.diagnostic(Severity.WARN, "No conversion from " + format + " to terminal text, shown as it is written");
				}
				int row = 0;
				for (String line: String.valueOf(value.get()).split("\n", -1)) {
					for (String piece: wrap(line, context.getColumns())) {
						context.put(0, row++, piece, TextStyle.PLAIN);
					}
				}
				return row;
			};
		}

	}

	/** A badge is its label in the logical color, in bold. */
	private static final class TerminalBadge implements Badge<TerminalContext> {

		@Override
		public Widget<Integer, TerminalContext> badge(Supplier<String> label, Variant variant) {
			return context -> {
				context.put(0, 0, String.valueOf(label.get()), TextStyle.PLAIN.withBold().withVariant(variant));
				return 1;
			};
		}

	}

	private static final class TerminalLink implements Link<TerminalContext> {

		@Override
		public Widget<Integer, TerminalContext> link(Widget<?, ? super TerminalContext> label, Supplier<String> target) {
			return context -> rows(label, context.styled(TextStyle.PLAIN.withUnderline().withVariant(Variant.PRIMARY).withLink(String.valueOf(target.get()))));
		}

	}

	// tag::section[]
	private static final class TerminalSection implements Section<TerminalContext> {

		private static final int INDENT = 2;

		@Override
		public Widget<Integer, TerminalContext> section(Widget<?, ? super TerminalContext> title, List<? extends Widget<?, ? super TerminalContext>> body) {
			return context -> {
				int row = rows(title, context.styled(TextStyle.PLAIN.withBold().withVariant(Variant.PRIMARY))); // <1>
				for (Widget<?, ? super TerminalContext> widget: body) {
					TerminalContext area = context.area(INDENT, row, context.getColumns() - INDENT, context.getRows() - row); // <2>
					row += rows(widget, area); // <3>
				}
				return row;
			};
		}

	}
	// end::section[]

	private static final class TerminalItemList implements ItemList<TerminalContext> {

		@Override
		public Widget<Integer, TerminalContext> list(boolean ordered, List<? extends Widget<?, ? super TerminalContext>> items) {
			return context -> {
				int row = 0;
				int number = 0;
				int width = ordered ? String.valueOf(items.size()).length() + 2 : 2;
				for (Widget<?, ? super TerminalContext> item: items) {
					if (row >= context.getRows()) {
						break;
					}
					String marker = ordered ? ++number + "." : "-";
					context.put(0, row, marker, TextStyle.PLAIN.withVariant(Variant.SECONDARY));
					row += Math.max(1, rows(item, context.area(width, row, context.getColumns() - width, context.getRows() - row)));
				}
				return row;
			};
		}

	}

	private static final class TerminalTable implements Table<TerminalContext> {

		@Override
		public <T> Widget<Integer, TerminalContext> table(List<Column<T, TerminalContext>> columns, Rows<T> rows) {
			return context -> {
				if (columns.isEmpty() || context.getRows() <= 0) {
					return 0;
				}
				int[] widths = widths(columns, context.getColumns());
				int[] offsets = new int[widths.length];
				for (int i = 1; i < widths.length; ++i) {
					offsets[i] = offsets[i - 1] + widths[i - 1] + 1;
				}

				int row = 0;
				int height = 1;
				TerminalContext header = context.styled(TextStyle.PLAIN.withBold());
				for (int i = 0; i < widths.length; ++i) {
					height = Math.max(height, rows(columns.get(i).header(), header.area(offsets[i], row, widths[i], context.getRows() - row)));
				}
				row += height;
				for (int i = 0; i < widths.length; ++i) {
					context.put(offsets[i], row, "-".repeat(widths[i]), TextStyle.PLAIN.withVariant(Variant.SECONDARY));
				}
				++row;

				for (int index = 0; index < rows.size() && row < context.getRows(); ++index) { // Rows below the area are never read
					T item = rows.get(index);
					height = 1;
					for (int i = 0; i < widths.length; ++i) {
						height = Math.max(height, rows(columns.get(i).cell().apply(item), context.area(offsets[i], row, widths[i], context.getRows() - row)));
					}
					row += height;
				}
				return row;
			};
		}

		/** Shares the width, less one column between neighbors, by the columns' weights. */
		private static <T> int[] widths(List<Column<T, TerminalContext>> columns, int total) {
			int available = Math.max(0, total - (columns.size() - 1));
			int weights = columns.stream().mapToInt(column -> Math.max(1, column.weight())).sum();
			int[] widths = new int[columns.size()];
			int used = 0;
			for (int i = 0; i < widths.length; ++i) {
				widths[i] = i == widths.length - 1 ? available - used : available * Math.max(1, columns.get(i).weight()) / weights;
				used += widths[i];
			}
			return widths;
		}

	}

}
