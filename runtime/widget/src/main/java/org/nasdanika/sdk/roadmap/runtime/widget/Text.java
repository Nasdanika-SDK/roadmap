package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.function.Supplier;

/**
 * The kind for text. Text is tagged with its format, and that is a security boundary as much as a
 * convenience: a plain string is never interpreted as markup. It is escaped for the target, so that
 * a value from a model or from a user cannot inject HTML, Markdown links or terminal escape
 * sequences. Text in the target's own format is passed through, and text in another format is
 * converted where a conversion exists, or reported and rendered as plain text where none does.
 *
 * <p>
 * A value is a {@link Supplier}, so a
 * {@link org.nasdanika.sdk.roadmap.runtime.common.flow.Binding} is a value: a static format reads it
 * when it renders, and a live one reads it on every frame.
 *
 * @param <C> The type of the context
 */
// tag::text[]
public interface Text<C> {

	/** Text with no markup: escaped for the target. */
	String PLAIN = "plain";

	String MARKDOWN = "markdown";

	String ASCIIDOC = "asciidoc";

	String HTML = "html";

	/**
	 * @param format The format of the value, one of the constants above or a format's own id
	 */
	Widget<?, C> text(Supplier<String> value, String format);

	default Widget<?, C> text(Supplier<String> value) {
		return text(value, PLAIN);
	}

	default Widget<?, C> text(String value) {
		return text(() -> value, PLAIN);
	}

}
// end::text[]
