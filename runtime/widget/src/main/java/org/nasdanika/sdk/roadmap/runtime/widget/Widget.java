package org.nasdanika.sdk.roadmap.runtime.widget;

/**
 * Something that renders itself into a context: Markdown written to a stream, an area of a
 * terminal, a document model under construction. The context's type is the format.
 *
 * <p>
 * A widget is a value. It is built before any context exists, holds no context, and can be
 * rendered many times into different instances of its context type: two terminals, a terminal on
 * every frame, a file and an answer to an agent. That is why a widget is asked for by context
 * <i>type</i>, through {@link Kinds}, and given the context <i>instance</i> only here.
 *
 * <p>
 * State that outlives one rendering, such as a scroll position or a selection, is not the widget's.
 * The screen owns it and the widget reads it, usually through a
 * {@link org.nasdanika.sdk.roadmap.runtime.common.flow.Binding}.
 *
 * @param <R> What rendering gives back, specific to the format: nothing, the rows used in a
 * terminal, the elements built in a document model for further refinement
 * @param <C> The type of the context the widget renders into. A widget for a context type renders
 * into its subtypes
 */
// tag::widget[]
@FunctionalInterface
public interface Widget<R, C> {

	R render(C context);

}
// end::widget[]
