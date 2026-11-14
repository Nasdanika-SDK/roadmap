package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.List;
import java.util.concurrent.Flow;

import org.nasdanika.sdk.runtime.common.Closeable;

/**
 * A waypoint that tells where the execution goes. A waypoint knows its parents and not its
 * children, so an execution can be read backward from any waypoint and cannot be followed forward
 * from its root. An observed root has a publisher of {@link WaypointEvent events}, which every
 * waypoint after it shares: a subscriber that wants the children of a waypoint builds them from
 * the arrivals, and the waypoints stay values.
 *
 * <p>
 * It decorates another waypoint and does not replace it, as {@link TelemetryWaypoint} does. Make it
 * the outermost decorator, so that the waypoint in an event carries everything: an event from an
 * observed waypoint inside a telemetry waypoint would not have the span.
 *
 * <h2>Leaving</h2>
 *
 * A step forward says "I am done here and I go there": the waypoint is left, and the next one is
 * arrived at. {@link #close()} says "I am done here and I go nowhere": the end of the work, a dead
 * end, a failed attempt. A waypoint is left once. A second step from it is a fork or a retry and
 * publishes only the arrival.
 *
 * @param <E> The type of the elements
 */
// tag::observed[]
public interface ObservedWaypoint<E> extends Waypoint<E>, Closeable {

	/**
	 * The events of the execution this waypoint belongs to: the publisher of its root.
	 */
	Flow.Publisher<WaypointEvent<E>> getEvents();

	/**
	 * Done here, and going nowhere. Idempotent, and does nothing after a step from this waypoint.
	 */
	@Override
	void close();
	// end::observed[]

	/**
	 * The waypoint this one observes.
	 */
	Waypoint<E> getDelegate();

	/**
	 * Whether the execution went on from this waypoint or closed it.
	 */
	boolean isLeft();

	@Override
	List<? extends ObservedWaypoint<E>> getParents();

	@Override
	ObservedWaypoint<E> commit(E next);

	/**
	 * A join leaves every observed waypoint it joins.
	 */
	@Override
	ObservedWaypoint<E> merge(E next, Iterable<? extends Waypoint<E>> additionalParents);

	/**
	 * Observes a waypoint the caller has already advanced to, from the delegate: the way to observe a
	 * step that changed the state, such as {@code observed.follow(state.commit(next, newState))}.
	 */
	ObservedWaypoint<E> follow(Waypoint<E> next);

}
