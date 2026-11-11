package org.nasdanika.sdk.roadmap.runtime.tui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.export.ExportRequest;
import dev.tamboui.layout.Rect;

/**
 * Screenshots of a TamboUI buffer, through the exporters TamboUI ships: SVG for a figure in a book
 * or on a page, and text for an assertion in a test. A buffer is what a screen draws into, so a
 * screenshot needs no terminal: draw the screen into a buffer, as a test does, and export it.
 */
public final class Screenshots {

	private Screenshots() {

	}

	/**
	 * A blank screen to draw into.
	 */
	public static Buffer buffer(int columns, int rows) {
		return Buffer.empty(Rect.of(columns, rows));
	}

	/**
	 * Draws into a new buffer through a context, for screens made of widgets.
	 */
	public static Buffer draw(int columns, int rows, Consumer<TamboUIContext> screen) {
		Buffer buffer = buffer(columns, rows);
		screen.accept(new TamboUIContext(buffer));
		return buffer;
	}

	/**
	 * The buffer as SVG, in a window frame with a title.
	 *
	 * <p>
	 * The SVG names the Fira Code font and loads it from a CDN, falling back to {@code monospace}.
	 * Each run of characters has its width set, so the grid holds in any monospace font. See
	 * {@link #selfContained(String)} for an SVG that loads nothing.
	 */
	// tag::svg[]
	public static String svg(Buffer buffer, String title) {
		return ExportRequest.export(buffer)
				.svg()
				.options(options -> options.title(title).chrome(true).uniqueId(id(title))) // <1>
				.toString()
				.replace("\r\n", "\n"); // <2>
	}
	// end::svg[]

	/**
	 * The buffer as lines of text, without styles, with a line feed between lines on every platform:
	 * the exporter uses the line separator of the platform it runs on.
	 */
	public static String text(Buffer buffer) {
		return ExportRequest.export(buffer).text().toString().replace("\r\n", "\n");
	}

	/**
	 * The SVG without its {@code @font-face} rules, so that it loads nothing from the network and is
	 * drawn in the monospace font of the viewer: for a book, a page behind a proxy, or a reader
	 * offline.
	 */
	// tag::self-contained[]
	public static String selfContained(String svg) {
		return svg.replaceAll("(?s)@font-face\\s*\\{.*?\\}", "");
	}
	// end::self-contained[]

	/**
	 * Writes a self-contained SVG screenshot to a file, creating its directory.
	 */
	public static Path write(Buffer buffer, String title, Path file) {
		try {
			Files.createDirectories(file.toAbsolutePath().getParent());
			return Files.writeString(file, selfContained(svg(buffer, title)), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** An id from the title, so that the same screen gives the same SVG, byte for byte. */
	private static String id(String title) {
		return "screen-" + title.toLowerCase().replaceAll("[^a-z0-9]+", "-");
	}

}
