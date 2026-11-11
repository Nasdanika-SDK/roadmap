package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.List;

/**
 * A waypoint over state that lives in a {@link Realm}: every waypoint of the execution reaches the
 * same guarded state, and reaches it only through commands. A command's closure captures the
 * waypoint, so no command needs the waypoint as an argument:
 * {@code waypoint.execute(state -> work(waypoint, state))}.
 *
 * @param <E> The type of the elements
 * @param <S> The type of the guarded state
 */
public interface RealmWaypoint<E, S> extends Waypoint<E>, Realm<S> {

	@Override
	List<? extends RealmWaypoint<E, S>> getParents();

	@Override
	RealmWaypoint<E, S> commit(E next);

	@Override
	RealmWaypoint<E, S> merge(E next, Iterable<? extends Waypoint<E>> additionalParents);

}
