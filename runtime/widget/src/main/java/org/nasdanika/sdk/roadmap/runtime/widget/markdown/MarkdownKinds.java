package org.nasdanika.sdk.roadmap.runtime.widget.markdown;

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
import org.nasdanika.sdk.roadmap.runtime.widget.markdown.MarkdownContext.Mode;

import io.opentelemetry.api.logs.Severity;

/**
 * The kinds of Markdown: text, links, sections and lists for {@link MarkdownContext}, and tables for
 * {@link GfmContext}. Every widget here gives back nothing, because Markdown is written and not
 * built.
 */
// tag::kinds[]
public class MarkdownKinds extends KindFactory {

	public MarkdownKinds() {
		super(
			new KindProvider<>(Text.class, MarkdownContext.class, new MarkdownText()),
			new KindProvider<>(Link.class, MarkdownContext.class, new MarkdownLink()),
			new KindProvider<>(Section.class, MarkdownContext.class, new MarkdownSection()),
			new KindProvider<>(ItemList.class, MarkdownContext.class, new MarkdownItemList()),
			new KindProvider<>(Badge.class, MarkdownContext.class, new MarkdownBadge()),
			new KindProvider<>(Table.class, GfmContext.class, new GfmTable())); // <1>
	}
	// end::kinds[]

	/** In a block a widget is a block of its own; inside a line or a cell it stays there. */
	private static Mode inner(MarkdownContext context) {
		return context.getMode() == Mode.BLOCK ? Mode.INLINE : context.getMode();
	}

	/** Renders a widget into a buffer of its own and returns what it wrote. */
	private static String render(Widget<?, ? super MarkdownContext> widget, MarkdownContext context, Mode mode) {
		MarkdownContext buffer = context.capture(mode);
		widget.render(buffer);
		return buffer.captured();
	}

	// tag::text[]
	private static final class MarkdownText implements Text<MarkdownContext> {

		@Override
		public Widget<Void, MarkdownContext> text(Supplier<String> value, String format) {
			return context -> {
				String text = String.valueOf(value.get());
				if (MARKDOWN.equals(format)) {
					context.emit(text); // <1>
				} else {
					if (!PLAIN.equals(format)) {
						context.diagnostic(Severity.WARN, "No conversion from " + format + " to Markdown, rendered as plain text"); // <2>
					}
					context.emit(context.escape(text)); // <3>
				}
				return null;
			};
		}

	}
	// end::text[]

	/** Markdown has no colors: a badge is strong text. */
	private static final class MarkdownBadge implements Badge<MarkdownContext> {

		@Override
		public Widget<Void, MarkdownContext> badge(Supplier<String> label, Variant variant) {
			return context -> {
				context.emit("**" + context.escape(String.valueOf(label.get())) + "**");
				return null;
			};
		}

	}

	private static final class MarkdownLink implements Link<MarkdownContext> {

		@Override
		public Widget<Void, MarkdownContext> link(Widget<?, ? super MarkdownContext> label, Supplier<String> target) {
			return context -> {
				String destination = String.valueOf(target.get()).replace("<", "%3C").replace(">", "%3E").replace("\n", "").replace("\r", "");
				context.emit("[" + render(label, context, inner(context)) + "](<" + destination + ">)");
				return null;
			};
		}

	}

	// tag::section[]
	private static final class MarkdownSection implements Section<MarkdownContext> {

		@Override
		public Widget<Void, MarkdownContext> section(Widget<?, ? super MarkdownContext> title, List<? extends Widget<?, ? super MarkdownContext>> body) {
			return context -> {
				if (context.getMode() != Mode.BLOCK) {
					context.diagnostic(Severity.WARN, "A section inside a line or a table cell: only its title is rendered");
					context.emit(render(title, context, context.getMode()));
					return null;
				}
				context.emit("#".repeat(Math.min(context.getLevel(), 6)) + " " + render(title, context, Mode.INLINE));
				MarkdownContext nested = context.nested(); // <1>
				for (Widget<?, ? super MarkdownContext> widget: body) {
					widget.render(nested);
				}
				return null;
			};
		}

	}
	// end::section[]

	private static final class MarkdownItemList implements ItemList<MarkdownContext> {

		@Override
		public Widget<Void, MarkdownContext> list(boolean ordered, List<? extends Widget<?, ? super MarkdownContext>> items) {
			return context -> {
				if (context.getMode() != Mode.BLOCK) {
					List<String> inline = new ArrayList<>();
					for (Widget<?, ? super MarkdownContext> item: items) {
						inline.add(render(item, context, context.getMode()));
					}
					context.emit(String.join(", ", inline));
					return null;
				}
				StringBuilder list = new StringBuilder();
				int number = 0;
				for (Widget<?, ? super MarkdownContext> item: items) {
					String marker = ordered ? ++number + ". " : "- ";
					String content = render(item, context, Mode.BLOCK);
					String indent = " ".repeat(marker.length());
					list.append(marker).append(content.replace("\n", "\n" + indent).replaceAll("(?m)[ \t]+$", "")).append('\n');
				}
				context.emit(list.toString().stripTrailing());
				return null;
			};
		}

	}

	private static final class GfmTable implements Table<GfmContext> {

		@Override
		public <T> Widget<Void, GfmContext> table(List<Column<T, GfmContext>> columns, Rows<T> rows) {
			return context -> {
				if (context.getMode() != Mode.BLOCK) {
					context.diagnostic(Severity.WARN, "A table inside a line or a table cell is not rendered");
					return null;
				}
				StringBuilder table = new StringBuilder("|");
				for (Column<T, GfmContext> column: columns) {
					table.append(' ').append(cell(column.header(), context)).append(" |");
				}
				table.append("\n|").append(" --- |".repeat(columns.size()));
				for (int i = 0; i < rows.size(); ++i) { // <1>
					T row = rows.get(i);
					table.append("\n|");
					for (Column<T, GfmContext> column: columns) {
						table.append(' ').append(cell(column.cell().apply(row), context)).append(" |");
					}
				}
				context.emit(table.toString());
				return null;
			};
		}

		private static String cell(Widget<?, ? super GfmContext> widget, GfmContext context) {
			GfmContext buffer = context.capture(Mode.CELL);
			widget.render(buffer);
			return buffer.captured();
		}

	}

}
