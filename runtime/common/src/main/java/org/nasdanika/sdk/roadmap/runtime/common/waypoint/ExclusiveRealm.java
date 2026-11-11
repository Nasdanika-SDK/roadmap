package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

/**
 * A realm in which at most one command runs at a time, and the others wait their turn. The type
 * carries the promise and adds no methods.
 *
 * @param <S> The type of the guarded state
 */
public interface ExclusiveRealm<S> extends Realm<S> {

}
