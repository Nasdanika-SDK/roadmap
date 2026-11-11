package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.List;

/**
 * The kind for a list of items, ordered or not. An item is a widget, so lists nest and an item can
 * be a link or a section.
 *
 * @param <C> The type of the context
 */
public interface ItemList<C> {

	Widget<?, C> list(boolean ordered, List<? extends Widget<?, ? super C>> items);

}
