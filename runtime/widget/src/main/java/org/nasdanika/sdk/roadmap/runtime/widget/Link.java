package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.function.Supplier;

/**
 * The kind for a link: a label that leads somewhere.
 *
 * @param <C> The type of the context
 */
public interface Link<C> {

	/**
	 * @param label What is shown
	 * @param target Where it leads: a URL, or a path and anchor in a format that writes several files
	 */
	Widget<?, C> link(Widget<?, ? super C> label, Supplier<String> target);

	default Widget<?, C> link(Widget<?, ? super C> label, String target) {
		return link(label, () -> target);
	}

}
