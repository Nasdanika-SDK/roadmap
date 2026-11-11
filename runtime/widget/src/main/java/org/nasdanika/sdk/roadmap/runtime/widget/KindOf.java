package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.Objects;

/**
 * The requirement the capability framework is asked: a kind, such as {@link Table}, for a type of
 * context, such as {@code GfmContext}. An explicit record rather than a generic type such as
 * {@code Table<GfmContext>}, because the answer may come from a provider for a supertype of the
 * context, and matching along a class hierarchy is this module's rule, not the type system's.
 *
 * @param kind The kind's interface
 * @param contextType The type of the context the widgets will be rendered into
 */
// tag::kind-of[]
public record KindOf(Class<?> kind, Class<?> contextType) {

	public KindOf {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(contextType, "contextType");
	}

}
// end::kind-of[]
