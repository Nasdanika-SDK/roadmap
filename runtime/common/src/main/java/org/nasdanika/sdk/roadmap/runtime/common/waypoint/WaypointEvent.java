package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

/**
 * What an observed execution tells its subscribers: it arrived at a waypoint, or it left one. An
 * arrival says where the execution came from, since a waypoint knows its parents, so the two events
 * are enough to follow an execution forward.
 *
 * @param <E> The type of the elements
 */
// tag::event[]
public sealed interface WaypointEvent<E> {

	ObservedWaypoint<E> waypoint();

	/**
	 * The execution is at the waypoint, reached from its parents.
	 */
	record Arrived<E>(ObservedWaypoint<E> waypoint) implements WaypointEvent<E> {

	}

	/**
	 * The execution is done at the waypoint: it went on from it, or it was closed.
	 */
	record Left<E>(ObservedWaypoint<E> waypoint) implements WaypointEvent<E> {

	}

}
// end::event[]
