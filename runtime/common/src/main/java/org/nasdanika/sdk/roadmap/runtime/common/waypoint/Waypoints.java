package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Root waypoints, kept in memory. What a root is decides what every waypoint after it carries:
 * nothing but lineage, a state, a realm, a span.
 */
public final class Waypoints {

	private Waypoints() {

	}

	/**
	 * A root that records lineage only.
	 */
	public static <E> Waypoint<E> root(E element) {
		return new Basic<>(element, List.of());
	}

	/**
	 * A root that carries a state.
	 */
	public static <E, S> StateWaypoint<E, S> root(E element, S state) {
		return new Stateful<>(element, state, List.of());
	}

	/**
	 * A root over state guarded by a realm, shared by every waypoint after it.
	 */
	public static <E, S> RealmWaypoint<E, S> realm(E element, Realm<S> realm) {
		return new Guarded<>(element, Objects.requireNonNull(realm, "realm"), List.of());
	}

	/**
	 * Adds telemetry to a root: a span for it, named by the function, under the current span. Every
	 * step from the result is a child span.
	 *
	 * @param spanName The name of the span of a waypoint at an element
	 */
	// tag::traced[]
	public static <E> TelemetryWaypoint<E> traced(Waypoint<E> root, Tracer tracer, Function<? super E, String> spanName) {
		Objects.requireNonNull(tracer, "tracer");
		Objects.requireNonNull(spanName, "spanName");
		Span span = tracer.spanBuilder(spanName.apply(root.getElement())).setParent(Context.current()).startSpan();
		return new Traced<>(root, List.of(), span, Context.current(), tracer, spanName);
	}
	// end::traced[]

	private static <E> List<Waypoint<E>> parents(Waypoint<E> first, Iterable<? extends Waypoint<E>> additional) {
		List<Waypoint<E>> parents = new ArrayList<>();
		parents.add(first);
		additional.forEach(parents::add);
		return List.copyOf(parents);
	}

	private record Basic<E>(E element, List<Waypoint<E>> parents) implements Waypoint<E> {

		@Override
		public E getElement() {
			return element;
		}

		@Override
		public List<? extends Waypoint<E>> getParents() {
			return parents;
		}

		@Override
		public Waypoint<E> commit(E next) {
			return new Basic<>(next, List.of(this));
		}

		@Override
		public Waypoint<E> merge(E next, Iterable<? extends Waypoint<E>> additionalParents) {
			return new Basic<>(next, Waypoints.parents(this, additionalParents));
		}

		// Identity, as commits have: two waypoints at one element are two visits
		@Override
		public boolean equals(Object other) {
			return this == other;
		}

		@Override
		public int hashCode() {
			return System.identityHashCode(this);
		}

		@Override
		public String toString() {
			return "Waypoint[" + element + "]";
		}

	}

	private static final class Stateful<E, S> implements StateWaypoint<E, S> {

		private final E element;
		private final S state;
		private final List<StateWaypoint<E, ?>> parents;

		Stateful(E element, S state, List<StateWaypoint<E, ?>> parents) {
			this.element = element;
			this.state = state;
			this.parents = parents;
		}

		@Override
		public E getElement() {
			return element;
		}

		@Override
		public S getState() {
			return state;
		}

		@Override
		public List<? extends StateWaypoint<E, ?>> getParents() {
			return parents;
		}

		@Override
		public <T> StateWaypoint<E, T> commit(E next, T nextState) {
			return new Stateful<>(next, nextState, List.of(this));
		}

		@Override
		public StateWaypoint<E, S> merge(E next, Iterable<? extends Waypoint<E>> additionalParents) {
			List<StateWaypoint<E, ?>> stateParents = new ArrayList<>();
			for (Waypoint<E> parent: additionalParents) {
				@SuppressWarnings("unchecked")
				StateWaypoint<E, ?> stateParent = parent.facet(StateWaypoint.class)
						.orElseThrow(() -> new IllegalArgumentException("Not a state waypoint: " + parent));
				stateParents.add(stateParent);
			}
			return merge(next, state, stateParents);
		}

		@Override
		public <T> StateWaypoint<E, T> merge(E next, T nextState, Iterable<? extends StateWaypoint<E, ?>> additionalParents) {
			List<StateWaypoint<E, ?>> all = new ArrayList<>();
			all.add(this);
			additionalParents.forEach(all::add);
			return new Stateful<>(next, nextState, List.copyOf(all));
		}

		@Override
		public String toString() {
			return "Waypoint[" + element + ", " + state + "]";
		}

	}

	private static final class Guarded<E, S> implements RealmWaypoint<E, S> {

		private final E element;
		private final Realm<S> realm;
		private final List<RealmWaypoint<E, S>> parents;

		Guarded(E element, Realm<S> realm, List<RealmWaypoint<E, S>> parents) {
			this.element = element;
			this.realm = realm;
			this.parents = parents;
		}

		@Override
		public E getElement() {
			return element;
		}

		@Override
		public List<? extends RealmWaypoint<E, S>> getParents() {
			return parents;
		}

		@Override
		public RealmWaypoint<E, S> commit(E next) {
			return new Guarded<>(next, realm, List.of(this));
		}

		@Override
		public RealmWaypoint<E, S> merge(E next, Iterable<? extends Waypoint<E>> additionalParents) {
			List<RealmWaypoint<E, S>> all = new ArrayList<>();
			all.add(this);
			for (Waypoint<E> parent: additionalParents) {
				@SuppressWarnings("unchecked")
				RealmWaypoint<E, S> realmParent = parent.facet(RealmWaypoint.class)
						.orElseThrow(() -> new IllegalArgumentException("Not a realm waypoint: " + parent));
				all.add(realmParent);
			}
			return new Guarded<>(next, realm, List.copyOf(all));
		}

		@Override
		public void execute(Consumer<? super S> command) {
			realm.execute(command);
		}

		@Override
		public <T> T apply(Function<? super S, ? extends T> command) {
			return realm.apply(command);
		}

		@Override
		public <F> Optional<F> facet(Class<F> type) {
			if (type.isInstance(this)) {
				return Optional.of(type.cast(this));
			}
			return type.isInstance(realm) ? Optional.of(type.cast(realm)) : Optional.empty();
		}

		@Override
		public String toString() {
			return "Waypoint[" + element + ", " + realm + "]";
		}

	}

	private static final class Traced<E> implements TelemetryWaypoint<E> {

		private final Waypoint<E> delegate;
		private final List<TelemetryWaypoint<E>> parents;
		private final Span span;
		private final Context context;
		private final Tracer tracer;
		private final Function<? super E, String> spanName;
		private final AtomicBoolean closed = new AtomicBoolean();

		Traced(Waypoint<E> delegate, List<TelemetryWaypoint<E>> parents, Span span, Context base, Tracer tracer, Function<? super E, String> spanName) {
			this.delegate = delegate;
			this.parents = parents;
			this.span = span;
			this.context = base.with(span);
			this.tracer = tracer;
			this.spanName = spanName;
		}

		@Override
		public E getElement() {
			return delegate.getElement();
		}

		@Override
		public Waypoint<E> getDelegate() {
			return delegate;
		}

		@Override
		public Span getSpan() {
			return span;
		}

		@Override
		public Context getContext() {
			return context;
		}

		@Override
		public List<? extends TelemetryWaypoint<E>> getParents() {
			return parents;
		}

		@Override
		public TelemetryWaypoint<E> commit(E next) {
			return follow(delegate.commit(next));
		}

		@Override
		public TelemetryWaypoint<E> follow(Waypoint<E> next) {
			return child(next, List.of());
		}

		// tag::merge[]
		@Override
		public TelemetryWaypoint<E> merge(E next, Iterable<? extends Waypoint<E>> additionalParents) {
			List<TelemetryWaypoint<E>> joining = new ArrayList<>();
			List<Waypoint<E>> delegates = new ArrayList<>();
			for (Waypoint<E> parent: additionalParents) {
				@SuppressWarnings("unchecked")
				Optional<TelemetryWaypoint<E>> traced = (Optional<TelemetryWaypoint<E>>) (Optional<?>) parent.facet(TelemetryWaypoint.class);
				traced.ifPresent(joining::add);
				delegates.add(traced.map(TelemetryWaypoint::getDelegate).orElse(parent));
			}
			return child(delegate.merge(next, delegates), joining); // <1>
		}

		private TelemetryWaypoint<E> child(Waypoint<E> next, List<TelemetryWaypoint<E>> joining) {
			SpanBuilder builder = tracer.spanBuilder(spanName.apply(next.getElement())).setParent(context);
			for (TelemetryWaypoint<E> other: joining) {
				builder.addLink(other.getSpan().getSpanContext()); // <2>
			}
			List<TelemetryWaypoint<E>> all = new ArrayList<>();
			all.add(this);
			all.addAll(joining);
			return new Traced<>(next, List.copyOf(all), builder.startSpan(), context, tracer, spanName);
		}
		// end::merge[]

		@Override
		public void run(Runnable command) {
			try (Scope scope = context.makeCurrent()) {
				command.run();
			}
		}

		@Override
		public <T> T call(Supplier<T> command) {
			try (Scope scope = context.makeCurrent()) {
				return command.get();
			}
		}

		@Override
		public void close() {
			if (closed.compareAndSet(false, true)) {
				span.end();
			}
		}

		@Override
		public <F> Optional<F> facet(Class<F> type) {
			if (type.isInstance(this)) {
				return Optional.of(type.cast(this));
			}
			if (type.isInstance(span)) {
				return Optional.of(type.cast(span));
			}
			return delegate.facet(type);
		}

		@Override
		public String toString() {
			return "Traced" + delegate;
		}

	}

}
