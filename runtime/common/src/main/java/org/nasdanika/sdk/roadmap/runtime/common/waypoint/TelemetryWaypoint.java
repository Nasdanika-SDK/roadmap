package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.List;
import java.util.function.Supplier;

import org.nasdanika.sdk.runtime.common.Closeable;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;

/**
 * A waypoint with a span: each step forward is a child span, and a join is a span whose parent is
 * the first parent's and which links to the others'. The tree of waypoints is the tree of spans.
 *
 * <p>
 * It decorates another waypoint and does not replace it, so telemetry is added to a plain, a state
 * or a realm waypoint alike, and the decorated waypoint is reached with {@link #getDelegate()} or
 * {@link #facet(Class)}.
 *
 * <p>
 * {@link #close()} ends the span. Closing is the caller's job, as with any span: a waypoint whose
 * work is done is closed, whether or not the execution went on from it.
 *
 * @param <E> The type of the elements
 */
public interface TelemetryWaypoint<E> extends Waypoint<E>, Closeable {

	/**
	 * The waypoint this one adds a span to.
	 */
	Waypoint<E> getDelegate();

	Span getSpan();

	/**
	 * The context with this waypoint's span as the current span, for propagation and for running
	 * work in it.
	 */
	Context getContext();

	@Override
	List<? extends TelemetryWaypoint<E>> getParents();

	@Override
	TelemetryWaypoint<E> commit(E next);

	@Override
	TelemetryWaypoint<E> merge(E next, Iterable<? extends Waypoint<E>> additionalParents);

	/**
	 * A child span for a waypoint the caller has already advanced to, from the delegate: the way to
	 * add telemetry to a step that changed the state, such as
	 * {@code traced.follow(state.commit(next, newState))}.
	 */
	TelemetryWaypoint<E> follow(Waypoint<E> next);

	/**
	 * Runs the command with this waypoint's span as the current span.
	 */
	void run(Runnable command);

	/**
	 * Computes a value with this waypoint's span as the current span.
	 */
	<T> T call(Supplier<T> command);

	/**
	 * Ends the span. Idempotent.
	 */
	@Override
	void close();

}
