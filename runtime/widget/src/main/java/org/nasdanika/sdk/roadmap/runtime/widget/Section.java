package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.List;

/**
 * The kind for a section: a title and what is under it. Sections nest, and the context knows how
 * deep it is: a heading level in Markdown, an indent or a border in a terminal.
 *
 * <p>
 * A widget asks for a section, and the context decides what a section is. A format that writes
 * several files may put a section in a file of its own.
 *
 * @param <C> The type of the context
 */
public interface Section<C> {

	Widget<?, C> section(Widget<?, ? super C> title, List<? extends Widget<?, ? super C>> body);

}
