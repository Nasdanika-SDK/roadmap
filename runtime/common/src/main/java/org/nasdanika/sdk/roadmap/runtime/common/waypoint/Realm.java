package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A bounded space of state whose rules, such as one thread at a time or a transaction around every
 * change, require that access goes through commands rather than through a reference to the state:
 * an editing domain, a connection, a resource set on a pump.
 *
 * <p>
 * The idiom is JFace's {@code Realm.exec}, EMF's command stack and Vert.x's {@code runOnContext}.
 * Methods are synchronous: on virtual threads a blocking call is cheap, which is the runtime's
 * rule for {@code Flows} as well.
 *
 * @param <S> The type of the guarded state
 */
// tag::realm[]
public interface Realm<S> {

	/**
	 * Runs a command against the guarded state and returns when it completes.
	 */
	void execute(Consumer<? super S> command);

	/**
	 * Computes a value from the guarded state.
	 */
	<T> T apply(Function<? super S, ? extends T> command);

	/**
	 * This realm seen as a realm of another state: a lens, not a snapshot. The adapter runs inside
	 * this realm on every access, so the view keeps this realm's rules.
	 */
	default <T> Realm<T> adapt(Function<? super S, ? extends T> adapter) {
		Realm<S> self = this;
		return new Realm<>() {

			@Override
			public void execute(Consumer<? super T> command) {
				self.execute(state -> command.accept(adapter.apply(state)));
			}

			@Override
			public <R> R apply(Function<? super T, ? extends R> command) {
				return self.apply(state -> command.apply(adapter.apply(state)));
			}

		};
	}

}
// end::realm[]
