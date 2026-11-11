package org.nasdanika.sdk.roadmap.runtime.common.flow;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.UnaryOperator;

/**
 * A binding that holds its value and can be set: the source end of a chain of bindings, and the
 * state of an input control.
 *
 * <p>
 * Setting a value equal to the current one does nothing. That is what lets two bindings that
 * follow each other settle: a round trip that arrives back with the same value stops there.
 *
 * @param <T> The type of the value
 */
public final class ValueBinding<T> extends AbstractBinding<T> {

	private static final String NULL_VALUE = "A binding's value is never null: bind an Optional for a value that can be absent";

	private volatile T value;

	ValueBinding(T initial, Executor executor) {
		super(executor);
		this.value = Objects.requireNonNull(initial, NULL_VALUE);
	}

	@Override
	public T get() {
		return value;
	}

	/**
	 * @return true if the value changed, false if it equals the current one
	 * @throws IllegalStateException if the binding is closed
	 */
	// tag::set[]
	public boolean set(T newValue) {
		Objects.requireNonNull(newValue, NULL_VALUE);
		synchronized (this) {
			if (isClosed()) {
				throw new IllegalStateException("The binding is closed");
			}
			if (newValue.equals(value)) {
				return false;
			}
			value = newValue;
		}
		changed(); // Outside the lock: subscribers read the value when they are served, not this one
		return true;
	}
	// end::set[]

	/**
	 * Sets the value computed from the current one, atomically.
	 *
	 * @return true if the value changed
	 */
	public boolean update(UnaryOperator<T> operator) {
		Objects.requireNonNull(operator, "operator");
		synchronized (this) {
			if (isClosed()) {
				throw new IllegalStateException("The binding is closed");
			}
			T newValue = Objects.requireNonNull(operator.apply(value), NULL_VALUE);
			if (newValue.equals(value)) {
				return false;
			}
			value = newValue;
		}
		changed();
		return true;
	}

	@Override
	public String toString() {
		return "ValueBinding[" + value + "]";
	}

}
