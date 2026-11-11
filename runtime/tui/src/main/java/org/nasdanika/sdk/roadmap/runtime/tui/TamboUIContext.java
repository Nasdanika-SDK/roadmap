package org.nasdanika.sdk.roadmap.runtime.tui;

import java.util.Objects;

import org.nasdanika.sdk.roadmap.runtime.widget.Variant;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.GridContext;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TerminalContext;
import org.nasdanika.sdk.roadmap.runtime.widget.terminal.TextStyle;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;

/**
 * A {@link TerminalContext} over an area of a TamboUI buffer: TamboUI as the first provider of the
 * terminal. Neutral widgets see a terminal context and never import TamboUI. A widget that needs
 * what TamboUI has asks for this context and reaches the buffer and the area through it, which is
 * the escape hatch a neutral API needs from its first version.
 */
// tag::context[]
public class TamboUIContext implements TerminalContext {

	private final Buffer buffer;
	private final Rect rect;
	private final TextStyle base;

	/**
	 * A context for a whole buffer.
	 */
	public TamboUIContext(Buffer buffer) {
		this(buffer, buffer.area(), null);
	}

	/**
	 * A context for an area of a buffer, as TamboUI passes them to a widget.
	 */
	public TamboUIContext(Buffer buffer, Rect rect) {
		this(buffer, rect, null);
	}

	private TamboUIContext(Buffer buffer, Rect rect, TextStyle base) {
		this.buffer = Objects.requireNonNull(buffer, "buffer");
		this.rect = Objects.requireNonNull(rect, "rect");
		this.base = base;
	}

	/** The buffer, for widgets written for TamboUI. */
	public Buffer getBuffer() {
		return buffer;
	}

	/** The area of the buffer this context draws into, in the buffer's coordinates. */
	public Rect getRect() {
		return rect;
	}
	// end::context[]

	@Override
	public double getWidth() {
		return rect.width();
	}

	@Override
	public double getHeight() {
		return rect.height();
	}

	@Override
	public TamboUIContext area(double x, double y, double width, double height) {
		int column = Math.clamp((int) x, 0, rect.width());
		int row = Math.clamp((int) y, 0, rect.height());
		Rect part = new Rect(
				rect.x() + column,
				rect.y() + row,
				Math.clamp((int) width, 0, rect.width() - column),
				Math.clamp((int) height, 0, rect.height() - row));
		return new TamboUIContext(buffer, part, base);
	}

	@Override
	public TamboUIContext area(int column, int row, int columns, int rows) {
		return area((double) column, (double) row, (double) columns, (double) rows);
	}

	@Override
	public TamboUIContext styled(TextStyle style) {
		return new TamboUIContext(buffer, rect, style.over(base));
	}

	// tag::put[]
	@Override
	public void put(int column, int row, String text, TextStyle style) {
		if (row < 0 || row >= rect.height() || column >= rect.width()) {
			return;
		}
		String clean = GridContext.sanitize(text); // <1>
		int from = Math.max(0, -column);
		int to = Math.min(clean.length(), rect.width() - column); // <2>
		if (from < to) {
			buffer.setString(rect.x() + column + from, rect.y() + row, clean.substring(from, to), style(style.over(base)));
		}
	}
	// end::put[]

	/**
	 * A logical style as a TamboUI style: logical colors on the palette of the terminal.
	 */
	// tag::style[]
	public static Style style(TextStyle style) {
		Style result = Style.EMPTY;
		if (style.variant() != null) {
			result = result.fg(color(style.variant()));
		}
		if (style.bold()) {
			result = result.bold();
		}
		if (style.underlined()) {
			result = result.underlined();
		}
		if (style.reversed()) {
			result = result.reversed();
		}
		if (style.link() != null && style.link().chars().noneMatch(Character::isISOControl)) { // <1>
			result = result.hyperlink(style.link());
		}
		return result;
	}

	public static Color color(Variant variant) {
		return switch (variant) {
			case PRIMARY -> Color.LIGHT_BLUE; // Plain blue is too dark to read on a dark background
			case SECONDARY -> Color.GRAY;
			case SUCCESS -> Color.GREEN;
			case DANGER -> Color.RED;
			case WARNING -> Color.YELLOW;
			case INFO -> Color.CYAN;
			case LIGHT -> Color.WHITE;
			case DARK -> Color.DARK_GRAY;
		};
	}
	// end::style[]

}
