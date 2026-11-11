package org.nasdanika.sdk.roadmap.runtime.widget.terminal;

import java.util.Arrays;
import java.util.function.BiConsumer;

import io.opentelemetry.api.logs.Severity;

/**
 * A terminal screen as a grid of characters in memory, with no terminal and no library: the
 * smallest provider of {@link TerminalContext}. Styles are dropped. It renders terminal widgets to
 * plain text, for a test, a log, a document, or an agent that reads a screen as text.
 */
public class GridContext implements TerminalContext {

	private final char[][] grid;
	private final int left;
	private final int top;
	private final int columns;
	private final int rows;
	private final BiConsumer<Severity, String> diagnostics;

	/**
	 * A blank screen.
	 */
	public GridContext(int columns, int rows) {
		this(columns, rows, null);
	}

	/**
	 * @param diagnostics Receives diagnostics. Null to report them as log records in the current span
	 */
	public GridContext(int columns, int rows, BiConsumer<Severity, String> diagnostics) {
		this(blank(columns, rows), 0, 0, columns, rows, diagnostics);
	}

	private GridContext(char[][] grid, int left, int top, int columns, int rows, BiConsumer<Severity, String> diagnostics) {
		this.grid = grid;
		this.left = left;
		this.top = top;
		this.columns = columns;
		this.rows = rows;
		this.diagnostics = diagnostics;
	}

	private static char[][] blank(int columns, int rows) {
		char[][] grid = new char[rows][columns];
		for (char[] row: grid) {
			Arrays.fill(row, ' ');
		}
		return grid;
	}

	@Override
	public double getWidth() {
		return columns;
	}

	@Override
	public double getHeight() {
		return rows;
	}

	// tag::area[]
	@Override
	public GridContext area(double x, double y, double width, double height) {
		int column = Math.clamp((int) x, 0, columns); // <1>
		int row = Math.clamp((int) y, 0, rows);
		return new GridContext(
				grid,
				left + column,
				top + row,
				Math.clamp((int) width, 0, columns - column),
				Math.clamp((int) height, 0, rows - row),
				diagnostics);
	}
	// end::area[]

	@Override
	public void put(int column, int row, String text, TextStyle style) {
		if (row < 0 || row >= rows) {
			return;
		}
		String clean = sanitize(text);
		for (int i = 0; i < clean.length(); ++i) {
			int at = column + i;
			if (at >= 0 && at < columns) {
				grid[top + row][left + at] = clean.charAt(i);
			}
		}
	}

	/**
	 * A grid has no styles, so the context is this one.
	 */
	@Override
	public GridContext styled(TextStyle style) {
		return this;
	}

	/**
	 * Text with every control character replaced, so that a value cannot move the cursor, change
	 * colors or start a hyperlink. Providers share it.
	 */
	// tag::sanitize[]
	public static String sanitize(String text) {
		StringBuilder clean = null;
		for (int i = 0; i < text.length(); ++i) {
			char ch = text.charAt(i);
			if (Character.isISOControl(ch)) { // <1>
				if (clean == null) {
					clean = new StringBuilder(text);
				}
				clean.setCharAt(i, ch == '\t' ? ' ' : '�');
			}
		}
		return clean == null ? text : clean.toString();
	}
	// end::sanitize[]

	@Override
	public void diagnostic(Severity severity, String message) {
		if (diagnostics == null) {
			TerminalContext.super.diagnostic(severity, message);
		} else {
			diagnostics.accept(severity, message);
		}
	}

	/**
	 * The screen as lines of text, without trailing spaces and without blank lines at the end.
	 */
	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		for (int row = 0; row < rows; ++row) {
			text.append(new String(grid[top + row], left, columns).stripTrailing()).append('\n');
		}
		return text.toString().stripTrailing() + "\n";
	}

}
