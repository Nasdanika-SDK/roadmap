package org.nasdanika.sdk.roadmap.runtime.widget;

import java.util.Objects;

/**
 * What a format contributes: an implementation of a kind for a type of context, which also serves
 * the subtypes of that context unless something more specific does.
 *
 * @param kind The kind's interface
 * @param contextType The type of context the implementation is written for
 * @param implementation An instance of the kind for that context type
 */
public record KindProvider<K>(Class<K> kind, Class<?> contextType, K implementation) {

	public KindProvider {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(contextType, "contextType");
		kind.cast(Objects.requireNonNull(implementation, "implementation"));
	}

	/**
	 * @return true if this provider can serve the requirement: the same kind, for this context type or a subtype of it
	 */
	public boolean serves(KindOf requirement) {
		return kind == requirement.kind() && contextType.isAssignableFrom(requirement.contextType());
	}

}
