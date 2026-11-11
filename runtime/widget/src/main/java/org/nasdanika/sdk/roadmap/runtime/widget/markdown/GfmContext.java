package org.nasdanika.sdk.roadmap.runtime.widget.markdown;

import java.util.function.BiConsumer;

import io.opentelemetry.api.logs.Severity;

/**
 * Where GitHub Flavored Markdown is written. It is Markdown, so every kind written for
 * {@link MarkdownContext} serves it, and it has tables, which CommonMark does not: the
 * {@code Table} kind is provided for this context and not for its parent.
 */
public class GfmContext extends MarkdownContext {

	public GfmContext(Appendable out) {
		super(out);
	}

	public GfmContext(Appendable out, int level, Mode mode, BiConsumer<Severity, String> diagnostics) {
		super(out, level, mode, diagnostics);
	}

	@Override
	protected MarkdownContext derive(Appendable out, int level, Mode mode, BiConsumer<Severity, String> diagnostics) {
		return new GfmContext(out, level, mode, diagnostics);
	}

	@Override
	public GfmContext nested() {
		return (GfmContext) super.nested();
	}

	@Override
	public GfmContext capture(Mode mode) {
		return (GfmContext) super.capture(mode);
	}

}
