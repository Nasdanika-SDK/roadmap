package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.List;
import java.util.function.Function;

/**
 * A waypoint that carries a state value. The state is read-only from the waypoint's point of view:
 * a transformation makes a new waypoint with a new state. For state that cannot be passed by
 * value, such as a resource set or a connection, see {@link RealmWaypoint}.
 *
 * @param <E> The type of the elements
 * @param <S> The type of the state at this waypoint. Parents may carry states of other types
 */
public interface StateWaypoint<E, S> extends Waypoint<E> {

	S getState();

	@Override
	List<? extends StateWaypoint<E, ?>> getParents();

	/**
	 * A step forward that carries the state unchanged.
	 */
	@Override
	default StateWaypoint<E, S> commit(E next) {
		return commit(next, getState());
	}

	/**
	 * A join that carries this waypoint's state unchanged. The additional parents must be state waypoints.
	 */
	@Override
	StateWaypoint<E, S> merge(E next, Iterable<? extends Waypoint<E>> additionalParents);

	/**
	 * A step forward with a new state.
	 */
	// tag::state[]
	<T> StateWaypoint<E, T> commit(E next, T nextState);

	/**
	 * A join with a new state.
	 */
	<T> StateWaypoint<E, T> merge(E next, T nextState, Iterable<? extends StateWaypoint<E, ?>> additionalParents);

	/**
	 * A join whose state is computed from the joining waypoints: this one first, then the additional
	 * parents in order.
	 */
	default <T> StateWaypoint<E, T> map(
			E next,
			Function<? super List<? extends StateWaypoint<E, ?>>, ? extends T> mapper,
			List<? extends StateWaypoint<E, ?>> additionalParents) {

		List<StateWaypoint<E, ?>> joining = new java.util.ArrayList<>();
		joining.add(this);
		joining.addAll(additionalParents);
		return merge(next, mapper.apply(List.copyOf(joining)), additionalParents);
	}
	// end::state[]

}
