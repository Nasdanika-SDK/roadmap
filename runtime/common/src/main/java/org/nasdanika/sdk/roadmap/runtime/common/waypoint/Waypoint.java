package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Where an execution is and how it got there, as it advances through some structure: a graph, a
 * file tree, a model, a state machine. A waypoint is a value: advancing makes a new waypoint and
 * leaves this one as it was, so the waypoints of an execution form a directed acyclic graph, as
 * commits do in Git, whose words this interface borrows.
 *
 * <p>
 * A waypoint may carry more than its lineage: state ({@link StateWaypoint}), a guarded space of
 * state ({@link RealmWaypoint}), a span ({@link TelemetryWaypoint}). Ask for what it carries with
 * {@link #facet(Class)}.
 *
 * @param <E> The type of the elements of the structure: a node, a path, a model element, a rule firing
 */
// tag::waypoint[]
public interface Waypoint<E> {

	/**
	 * The element this waypoint records the execution at.
	 */
	E getElement();

	/**
	 * The waypoints this one was reached from, in creation order: none for a root, one for a step
	 * forward, several for a join.
	 */
	List<? extends Waypoint<E>> getParents();

	/**
	 * A step forward: a waypoint at the next element with this one as its only parent.
	 */
	Waypoint<E> commit(E next);

	/**
	 * A join: a waypoint at the next element with this one as its first parent and the others after it.
	 */
	Waypoint<E> merge(E next, Iterable<? extends Waypoint<E>> additionalParents);
	// end::waypoint[]

	default int getParentCount() {
		return getParents().size();
	}

	default Waypoint<E> getParent(int index) {
		return getParents().get(index);
	}

	/**
	 * What this waypoint carries besides its lineage, by type: {@code facet(StateWaypoint.class)},
	 * {@code facet(Span.class)}. One way to add capabilities, as {@code GenericVolume.capability} is
	 * for volumes, so that facets do not multiply interfaces.
	 */
	default <F> Optional<F> facet(Class<F> type) {
		return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
	}

	/**
	 * This waypoint and every waypoint it was reached through, nearest first, each once.
	 * Lazy: a rule that stops at the first match does not walk the whole history.
	 */
	// tag::lineage[]
	default Stream<Waypoint<E>> lineage() {
		Deque<Waypoint<E>> queue = new ArrayDeque<>();
		Set<Waypoint<E>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		queue.add(this);
		seen.add(this);
		return Stream.generate(() -> {
			Waypoint<E> next = queue.poll();
			if (next != null) {
				for (Waypoint<E> parent: next.getParents()) {
					if (seen.add(parent)) {
						queue.add(parent);
					}
				}
			}
			return next;
		}).takeWhile(Objects::nonNull);
	}
	// end::lineage[]

	/**
	 * How many times the execution has been at the element, this waypoint included. Rules that
	 * decide whether to go on, such as "stop on the third pass", are rules over lineage.
	 */
	default long visits(E element) {
		return lineage().filter(waypoint -> Objects.equals(waypoint.getElement(), element)).count();
	}

}
