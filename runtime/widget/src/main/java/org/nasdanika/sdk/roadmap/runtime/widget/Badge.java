package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.function.Supplier;

/**
 * The kind for a short label with a logical color: a status, a severity, a count. The view says
 * what the label means with a {@link Variant}, and the format shows it its own way: a colored cell
 * in a terminal, strong text in Markdown, a badge in Bootstrap.
 *
 * @param <C> The type of the context
 */
public interface Badge<C> {

	Widget<?, C> badge(Supplier<String> label, Variant variant);

	default Widget<?, C> badge(String label, Variant variant) {
		return badge(() -> label, variant);
	}

}
