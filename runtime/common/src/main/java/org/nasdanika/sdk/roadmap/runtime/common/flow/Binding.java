package org.nasdanika.sdk.roadmap.runtime.common.flow;

import java.util.concurrent.Flow;
import java.util.function.Supplier;

import org.nasdanika.sdk.runtime.common.Closeable;

/**
 * A value that can be read now and followed as it changes.
 *
 * <ul>
 * <li>{@link #get()} is the value now. A static render, such as Markdown, calls it and never
 * subscribes.</li>
 * <li>{@link #subscribe(Flow.Subscriber)} delivers the value at subscription, then each new value,
 * <b>coalesced to the latest</b>: a subscriber that asks for one item at a time gets the value as it
 * is when it asks, and never a backlog of the values in between. A subscriber is not told about a
 * value equal to the last one it received.</li>
 * <li>{@link #close()} ends the binding: it completes its subscribers and releases whatever it
 * holds upstream. It is idempotent, and {@code get()} keeps returning the last value, so a finished
 * session can still be rendered.</li>
 * </ul>
 *
 * <p>
 * The value is never null, because {@link Flow} forbids null items. A value that can be absent is a
 * binding of {@link java.util.Optional}.
 *
 * <p>
 * Because the first item is the value at subscription, there is no gap between reading the value
 * and starting to follow it. Kotlin's {@code StateFlow} and RxJava's {@code BehaviorSubject} work the
 * same way.
 *
 * @param <T> The type of the value
 */
// tag::binding[]
public interface Binding<T> extends Supplier<T>, Flow.Publisher<T>, Closeable {

	/**
	 * @return true once {@link #close()} was called, or the binding this one follows ended
	 */
	boolean isClosed();

}
// end::binding[]
