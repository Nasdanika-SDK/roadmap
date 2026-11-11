package org.nasdanika.sdk.roadmap.runtime.common.flow;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The publisher half of a {@link Binding}: subscribers, each with a slot that says "changed since
 * you last looked", and delivery through an executor.
 *
 * <h2>Back pressure is coalescing, not blocking</h2>
 *
 * A source calls {@link #changed()}, which marks each subscriber's slot and returns. It never waits
 * for a subscriber, so a setter inside an EMF notification is never held back. When a subscriber has
 * demand, it is given the value as it is <i>then</i>, read with {@link #get()}. A source that changes
 * a thousand times between two requests costs a thousand writes of one flag, and one delivery.
 *
 * <h2>The executor is the realm</h2>
 *
 * Signals to a subscriber are delivered by tasks handed to the executor, one at a time per
 * subscriber. With a {@link org.nasdanika.sdk.runtime.common.flow.Pump} as the executor, every
 * signal is a pump task: it runs in the OpenTelemetry context that was current when the change was
 * made, and nothing is delivered until somebody pumps, which is how a frame loop sets the pace.
 * Without an executor, signals are delivered on the thread that made the change.
 *
 * @param <T> The type of the value
 */
public abstract class AbstractBinding<T> implements Binding<T> {

	private static final Object NONE = new Object();

	private final Executor executor;
	private final List<Slot> slots = new CopyOnWriteArrayList<>();
	private final AtomicBoolean closed = new AtomicBoolean();

	/**
	 * @param executor Delivers signals to subscribers. Null to deliver on the thread that made the change
	 */
	protected AbstractBinding(Executor executor) {
		this.executor = executor == null ? Runnable::run : executor;
	}

	/**
	 * Tells subscribers that {@link #get()} may now return another value. Never blocks, and may be
	 * called from any thread.
	 */
	protected void changed() {
		for (Slot slot: slots) {
			slot.signal();
		}
	}

	/**
	 * Called once, by the first {@link #close()}, before subscribers are completed. Release what the
	 * binding holds upstream here.
	 */
	protected void onClose() {

	}

	@Override
	public boolean isClosed() {
		return closed.get();
	}

	// tag::close[]
	@Override
	public void close() {
		if (closed.compareAndSet(false, true)) {
			onClose();
			for (Slot slot: slots) {
				slot.complete();
			}
		}
	}
	// end::close[]

	@Override
	public void subscribe(Flow.Subscriber<? super T> subscriber) {
		Objects.requireNonNull(subscriber, "subscriber");
		Slot slot = new Slot(subscriber);
		slots.add(slot); // Before onSubscribe, so that a change made during it is not missed
		subscriber.onSubscribe(slot);
		if (isClosed()) {
			slot.complete();
		}
	}

	/**
	 * @return The number of current subscribers
	 */
	public int getSubscriberCount() {
		return slots.size();
	}

	private final class Slot implements Flow.Subscription {

		private final Flow.Subscriber<? super T> subscriber;
		private final AtomicLong requested = new AtomicLong();
		private final AtomicInteger wip = new AtomicInteger();
		private final AtomicBoolean dirty = new AtomicBoolean(true); // The value at subscription is the first item
		private volatile boolean cancelled;
		private volatile boolean completing;
		private volatile Throwable failure;

		// Touched only by the drain loop
		private Object last = NONE;
		private boolean done;

		Slot(Flow.Subscriber<? super T> subscriber) {
			this.subscriber = subscriber;
		}

		@Override
		public void request(long n) {
			if (n <= 0) {
				failure = new IllegalArgumentException("Demand must be positive: " + n);
			} else {
				requested.accumulateAndGet(n, (current, more) -> current + more < 0 ? Long.MAX_VALUE : current + more);
			}
			schedule();
		}

		@Override
		public void cancel() {
			cancelled = true;
			slots.remove(this);
		}

		void signal() {
			dirty.set(true);
			schedule();
		}

		void complete() {
			completing = true;
			schedule();
		}

		/**
		 * At most one drain task per subscriber is queued or running: signals that arrive meanwhile
		 * are counted, and the running task goes around again.
		 */
		private void schedule() {
			if (wip.getAndIncrement() == 0) {
				executor.execute(this::drain);
			}
		}

		private void drain() {
			int missed = 1;
			do {
				try {
					step();
				} catch (Throwable e) {
					// A subscriber that throws has broken the contract: it is dropped, and the failure goes to
					// whoever runs the task, the setter or the pump
					cancelled = true;
					slots.remove(this);
					wip.set(0);
					throw e;
				}
				missed = wip.addAndGet(-missed);
			} while (missed != 0);
		}

		// tag::step[]
		private void step() {
			if (done || cancelled) {
				return;
			}
			Throwable error = failure;
			if (error != null) {
				end();
				subscriber.onError(error);
				return;
			}
			if (requested.get() > 0 && dirty.compareAndSet(true, false)) {
				T value;
				try {
					value = Objects.requireNonNull(get(), "A binding's value is never null");
				} catch (RuntimeException e) {
					end();
					subscriber.onError(e);
					return;
				}
				if (!value.equals(last)) { // <1>
					last = value;
					if (requested.get() != Long.MAX_VALUE) {
						requested.decrementAndGet();
					}
					subscriber.onNext(value);
				}
			}
			if (completing && !done && !cancelled) {
				end();
				subscriber.onComplete();
			}
		}
		// end::step[]

		private void end() {
			done = true;
			slots.remove(this);
		}

	}

}
