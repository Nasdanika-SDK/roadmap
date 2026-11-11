package org.nasdanika.sdk.roadmap.runtime.tui;

import java.util.List;

import org.nasdanika.sdk.roadmap.runtime.widget.KindFactory;
import org.nasdanika.sdk.roadmap.runtime.widget.KindProvider;
import org.nasdanika.sdk.roadmap.runtime.widget.Section;
import org.nasdanika.sdk.roadmap.runtime.widget.Variant;
import org.nasdanika.sdk.roadmap.runtime.widget.Widget;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalKinds;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TextStyle;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.layout.Rect;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;

/**
 * The kinds that TamboUI does better than the neutral terminal kinds. Everything else comes from
 * {@link TerminalKinds}, which serves {@link TamboUIContext} because it is a
 * {@link TerminalContext}.
 */
public class TamboUIKinds extends KindFactory {

	public TamboUIKinds() {
		super(new KindProvider<>(Section.class, TamboUIContext.class, new BlockSection()));
	}

	/**
	 * A section as a TamboUI block: a border with the title on it. The neutral section
	 * indents; this one draws a box, which only this context can do.
	 */
	// tag::section[]
	private static final class BlockSection implements Section<TamboUIContext> {

		@Override
		public Widget<Integer, TamboUIContext> section(Widget<?, ? super TamboUIContext> title, List<? extends Widget<?, ? super TamboUIContext>> body) {
			return context -> {
				Rect rect = context.getRect();
				if (rect.width() < 6 || rect.height() < 3) {
					return TerminalKinds.rows(title, context);
				}

				// The body, drawn off screen first: its height is the height of the box
				Buffer scratch = Buffer.empty(Rect.of(rect.width() - 4, rect.height() - 2));
				TamboUIContext inner = new TamboUIContext(scratch); // <1>
				int rows = 0;
				for (Widget<?, ? super TamboUIContext> widget: body) {
					rows += TerminalKinds.rows(widget, inner.area(0, rows, inner.getColumns(), inner.getRows() - rows));
				}
				rows = Math.min(rows, rect.height() - 2);

				Block.builder() // <2>
						.borders(Borders.ALL)
						.borderType(BorderType.PLAIN)
						.borderColor(TamboUIContext.color(Variant.SECONDARY))
						.build()
						.render(new Rect(rect.x(), rect.y(), rect.width(), rows + 2), context.getBuffer());

				for (int y = 0; y < rows; ++y) {
					for (int x = 0; x < scratch.width(); ++x) {
						Cell cell = scratch.get(x, y);
						if (!cell.isEmpty()) {
							context.getBuffer().set(rect.x() + 2 + x, rect.y() + 1 + y, cell);
						}
					}
				}

				TamboUIContext heading = context.styled(TextStyle.PLAIN.withBold().withVariant(Variant.PRIMARY)).area(2, 0, rect.width() - 4, 1);
				TerminalKinds.rows(title, heading); // <3>
				return rows + 2;
			};
		}

	}
	// end::section[]

}
