package org.nasdanika.sdk.roadmap.runtime.widget.terminal;

import org.nasdanika.sdk.roadmap.runtime.widget.SurfaceContext;

/**
 * The drawn area of a terminal screen, in cells: what terminal widgets are written against. A
 * provider implements it over a terminal library. TamboUI is the first, in the {@code tui} module,
 * and {@link GridContext} is a second with no library at all.
 *
 * <p>
 * It abstracts only what the neutral kinds need: an area, text at a position, and logical styles.
 * Everything else a library has stays in the provider's own context, which extends this one, so a
 * widget that needs more asks for that context and gets it.
 *
 * <p>
 * A widget for this context gives back the number of rows it used, as an {@code Integer}, which is
 * how widgets are stacked without a second pass to measure them.
 */
// tag::terminal[]
public interface TerminalContext extends SurfaceContext {

	@Override
	TerminalContext area(double x, double y, double width, double height);

	default TerminalContext area(int column, int row, int columns, int rows) {
		return area((double) column, (double) row, (double) columns, (double) rows);
	}

	default int getColumns() {
		return (int) getWidth();
	}

	default int getRows() {
		return (int) getHeight();
	}

	/**
	 * Writes text on one row, starting at a column, both relative to this area. What does not fit the
	 * area is not written, and control characters never reach the terminal.
	 */
	void put(int column, int row, String text, TextStyle style);

	/**
	 * A context for the same area in which text takes the given style unless it brings its own: how
	 * a section makes its title bold without knowing what the title is.
	 */
	TerminalContext styled(TextStyle style);

}
// end::terminal[]
