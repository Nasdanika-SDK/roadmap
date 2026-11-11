package org.nasdanika.sdk.roadmap.runtime.common.flow;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.notify.impl.AdapterImpl;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;

/**
 * Factories of {@link Binding}s: values that can be set, values computed from other bindings, and
 * the bridge to EMF notifications.
 *
 * <p>
 * The operators of {@link org.nasdanika.sdk.runtime.common.flow.Flows} apply to a binding, because a
 * binding is a publisher, but they return a plain publisher and lose {@code get()}. The methods
 * here map both sides.
 */
public final class Bindings {

	private Bindings() {

	}

	/**
	 * A binding that can be set, delivering to subscribers on the thread that sets it.
	 */
	public static <T> ValueBinding<T> value(T initial) {
		return new ValueBinding<>(initial, null);
	}

	/**
	 * A binding that can be set, delivering to subscribers through the executor. Pass a
	 * {@link org.nasdanika.sdk.runtime.common.flow.Pump} to deliver in pump tasks, in the telemetry
	 * context of the change and at the pace of whoever pumps.
	 */
	public static <T> ValueBinding<T> value(T initial, Executor executor) {
		return new ValueBinding<>(initial, executor);
	}

	/**
	 * A binding computed from another: a converter. The new binding follows the source, and closing
	 * it stops following without closing the source, which it does not own. It ends when the source
	 * ends.
	 */
	// tag::map[]
	public static <T, R> Binding<R> map(Binding<T> source, Function<? super T, ? extends R> mapper) {
		Objects.requireNonNull(mapper, "mapper");
		return derive(() -> mapper.apply(source.get()), source);
	}
	// end::map[]

	/**
	 * A binding computed from two others.
	 */
	public static <A, B, R> Binding<R> combine(Binding<A> first, Binding<B> second, BiFunction<? super A, ? super B, ? extends R> combiner) {
		Objects.requireNonNull(combiner, "combiner");
		return derive(() -> combiner.apply(first.get(), second.get()), first, second);
	}

	/**
	 * A binding computed from any number of others, as a spreadsheet cell is computed from the
	 * cells its formula names: the value is computed when it is asked for, and subscribers are told
	 * to ask again when a source changes.
	 *
	 * @param value Computes the value from the sources' current values. Side-effect free
	 * @param sources The bindings the value depends on. The new binding ends when all of them have
	 */
	public static <R> Binding<R> derive(Supplier<? extends R> value, Binding<?>... sources) {
		return derive(value, null, sources);
	}

	/**
	 * As {@link #derive(Supplier, Binding...)}, delivering to subscribers through the executor.
	 */
	public static <R> Binding<R> derive(Supplier<? extends R> value, Executor executor, Binding<?>... sources) {
		Objects.requireNonNull(value, "value");
		return new DerivedBinding<>(value, executor, sources);
	}

	/**
	 * The bridge from EMF notifications: a binding of a feature's value, empty when the feature is
	 * null or unset. A many-valued feature is bound as an unmodifiable copy of its list, so that a
	 * subscriber is told that the list changed and reads it again.
	 *
	 * <p>
	 * The binding listens with an adapter on the object, and {@link Binding#close()} removes it.
	 * The notification is not held back: it marks the subscribers' slots and returns to the setter.
	 *
	 * @param type The type of the value: the feature's instance class, or {@code List} for a many-valued feature
	 */
	// tag::emf[]
	public static <T> Binding<Optional<T>> of(EObject object, EStructuralFeature feature, Class<T> type) {
		return of(object, feature, type, null);
	}
	// end::emf[]

	/**
	 * As {@link #of(EObject, EStructuralFeature, Class)}, delivering to subscribers through the executor.
	 */
	public static <T> Binding<Optional<T>> of(EObject object, EStructuralFeature feature, Class<T> type, Executor executor) {
		Objects.requireNonNull(object, "object");
		Objects.requireNonNull(feature, "feature");
		Objects.requireNonNull(type, "type");
		return new FeatureBinding<>(object, feature, type, executor);
	}

	private static final class DerivedBinding<R> extends AbstractBinding<R> {

		private final Supplier<? extends R> value;
		private final List<Follower> followers;
		private final AtomicInteger live;

		DerivedBinding(Supplier<? extends R> value, Executor executor, Binding<?>[] sources) {
			super(executor);
			this.value = value;
			this.live = new AtomicInteger(sources.length);
			this.followers = java.util.Arrays.stream(sources).map(source -> {
				Follower follower = new Follower();
				source.subscribe(follower);
				return follower;
			}).toList();
		}

		@Override
		public R get() {
			return value.get();
		}

		@Override
		protected void onClose() {
			if (followers != null) { // Null when every source had already ended while this binding was being constructed
				followers.forEach(Follower::cancel);
			}
		}

		private final class Follower implements Flow.Subscriber<Object> {

			private volatile Flow.Subscription subscription;

			@Override
			public void onSubscribe(Flow.Subscription subscription) {
				this.subscription = subscription;
				subscription.request(Long.MAX_VALUE); // The source coalesces: this never sees a backlog
			}

			@Override
			public void onNext(Object item) {
				changed(); // The item is not used: the value is computed from the sources when it is asked for
			}

			@Override
			public void onError(Throwable throwable) {
				ended();
			}

			@Override
			public void onComplete() {
				ended();
			}

			private void ended() {
				if (live.decrementAndGet() == 0) {
					close();
				}
			}

			void cancel() {
				Flow.Subscription s = subscription;
				if (s != null) {
					s.cancel();
				}
			}

		}

	}

	private static final class FeatureBinding<T> extends AbstractBinding<Optional<T>> {

		private final EObject object;
		private final EStructuralFeature feature;
		private final Class<T> type;
		private final Adapter adapter = new AdapterImpl() {

			@Override
			public void notifyChanged(Notification notification) {
				if (notification.getFeature() == feature && !notification.isTouch()) {
					changed();
				}
			}

		};

		FeatureBinding(EObject object, EStructuralFeature feature, Class<T> type, Executor executor) {
			super(executor);
			this.object = object;
			this.feature = feature;
			this.type = type;
			object.eAdapters().add(adapter);
		}

		@Override
		public Optional<T> get() {
			Object value = object.eGet(feature);
			if (value instanceof List<?> list && feature.isMany()) {
				value = List.copyOf(list);
			}
			return Optional.ofNullable(type.cast(value));
		}

		@Override
		protected void onClose() {
			object.eAdapters().remove(adapter);
		}

	}

}
