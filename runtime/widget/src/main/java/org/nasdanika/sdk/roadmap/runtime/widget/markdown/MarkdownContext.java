package org.nasdanika.sdk.roadmap.runtime.widget.markdown;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.nasdanika.sdk.roadmap.runtime.widget.RenderContext;

import io.opentelemetry.api.logs.Severity;

/**
 * Where Markdown is written: CommonMark, with nothing a plain Markdown reader would not understand.
 * The context knows how deep it is in sections and whether it is writing a block, a run of text in
 * a line, or a table cell, and it escapes plain text for where it is.
 *
 * <p>
 * Nested widgets render into derived contexts: one heading level down ({@link #nested()}), or into
 * a buffer of their own ({@link #capture(Mode)}) that the enclosing widget places, as a list places
 * its items behind their markers. Derived contexts are of the same class as the context they come
 * from, so a widget written for a subclass keeps finding it.
 */
public class MarkdownContext implements RenderContext {

	/** Where in a document the context writes. */
	public enum Mode {

		/** Paragraphs, headings, lists and tables, each followed by a blank line. */
		BLOCK,

		/** Text within a line: a heading, a link's label, a list item's first line. */
		INLINE,

		/** A table cell: text within a line where a pipe would end the cell. */
		CELL

	}

	private final Appendable out;
	private final int level;
	private final Mode mode;
	private final BiConsumer<Severity, String> diagnostics;

	/**
	 * A context for a document: block mode, with top-level sections at heading level 1.
	 */
	public MarkdownContext(Appendable out) {
		this(out, 1, Mode.BLOCK, null);
	}

	/**
	 * @param level The heading level of a section rendered into this context
	 * @param diagnostics Receives diagnostics. Null to report them as log records in the current span
	 */
	public MarkdownContext(Appendable out, int level, Mode mode, BiConsumer<Severity, String> diagnostics) {
		this.out = Objects.requireNonNull(out, "out");
		this.level = level;
		this.mode = Objects.requireNonNull(mode, "mode");
		this.diagnostics = diagnostics;
	}

	/**
	 * Creates a derived context. Subclasses override it to return their own class.
	 */
	protected MarkdownContext derive(Appendable out, int level, Mode mode, BiConsumer<Severity, String> diagnostics) {
		return new MarkdownContext(out, level, mode, diagnostics);
	}

	public int getLevel() {
		return level;
	}

	public Mode getMode() {
		return mode;
	}

	/**
	 * A context one heading level down, writing to the same place: for the body of a section.
	 */
	public MarkdownContext nested() {
		return derive(out, level + 1, Mode.BLOCK, diagnostics);
	}

	/**
	 * A context that writes into a buffer of its own, at the same heading level. The enclosing
	 * widget reads what was written with {@link #captured()} and places it.
	 */
	// tag::capture[]
	public MarkdownContext capture(Mode mode) {
		return derive(new StringBuilder(), level, mode, diagnostics);
	}

	/**
	 * @return What was written into a context made by {@link #capture(Mode)}, without the blank lines at its end
	 */
	public String captured() {
		if (out instanceof StringBuilder buffer) {
			return buffer.toString().stripTrailing();
		}
		throw new IllegalStateException("Not a capturing context");
	}
	// end::capture[]

	/**
	 * Writes Markdown as it is: a block followed by a blank line in block mode, a run of text otherwise.
	 */
	public void emit(String markdown) {
		try {
			out.append(markdown);
			if (mode == Mode.BLOCK) {
				out.append("\n\n");
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Plain text as Markdown that shows exactly that text, for where this context writes.
	 */
	// tag::escape[]
	public String escape(String plain) {
		StringBuilder escaped = new StringBuilder(plain.length() + 16);
		boolean lineStart = true;
		for (int i = 0; i < plain.length(); ++i) {
			char ch = plain.charAt(i);
			switch (ch) {
				case '\\', '`', '*', '_', '[', ']', '<', '>', '~' -> escaped.append('\\').append(ch); // <1>
				case '&' -> escaped.append("&amp;");
				case '|' -> escaped.append(mode == Mode.CELL ? "\\|" : "|"); // <2>
				case '#', '+', '-', '=' -> escaped.append(lineStart ? "\\" : "").append(ch); // <3>
				case '.', ')' -> escaped.append(lineStart(plain, i) ? "\\" : "").append(ch); // <4>
				case '\r' -> { }
				case '\n' -> escaped.append(mode == Mode.BLOCK ? "\n" : " "); // <5>
				default -> escaped.append(Character.isISOControl(ch) ? ' ' : ch);
			}
			lineStart = ch == '\n' || (lineStart && ch == ' ');
		}
		return escaped.toString();
	}
	// end::escape[]

	/**
	 * @return true if the character at the index follows only digits on its line, as the dot of "1." does
	 */
	private static boolean lineStart(String text, int index) {
		int i = index - 1;
		while (i >= 0 && Character.isDigit(text.charAt(i))) {
			--i;
		}
		if (i == index - 1) {
			return false;
		}
		while (i >= 0 && text.charAt(i) == ' ') {
			--i;
		}
		return i < 0 || text.charAt(i) == '\n';
	}

	@Override
	public void diagnostic(Severity severity, String message) {
		if (diagnostics == null) {
			RenderContext.super.diagnostic(severity, message);
		} else {
			diagnostics.accept(severity, message);
		}
	}

}
