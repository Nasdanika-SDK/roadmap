package org.nasdanika.sdk.roadmap.runtime.common.waypoint;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import org.nasdanika.sdk.runtime.common.flow.Pump;

/**
 * Realms over plain state.
 */
public final class Realms {

	private Realms() {

	}

	/**
	 * A realm that guards the state with a lock: one command at a time, on the calling thread.
	 * Re-entrant, so a command may issue commands.
	 */
	public static <S> ExclusiveRealm<S> exclusive(S state) {
		ReentrantLock lock = new ReentrantLock();
		return new ExclusiveRealm<>() {

			@Override
			public void execute(Consumer<? super S> command) {
				lock.lock();
				try {
					command.accept(state);
				} finally {
					lock.unlock();
				}
			}

			@Override
			public <T> T apply(Function<? super S, ? extends T> command) {
				lock.lock();
				try {
					return command.apply(state);
				} finally {
					lock.unlock();
				}
			}

		};
	}

	/**
	 * A realm whose commands are tasks of a pump. See {@link PumpRealm}.
	 */
	public static <S> PumpRealm<S> pump(S state, Pump pump) {
		return new PumpRealm<>(state, pump);
	}

	/**
	 * A realm whose commands are tasks of a {@link Pump}, which runs one task at a time: the pump
	 * is the realm, as the user interface thread is JFace's. A command runs in the OpenTelemetry
	 * context that was current when it was issued, so what it changes is traced to where the command
	 * came from.
	 *
	 * <p>
	 * {@link #apply(Function)} and {@link #execute(Consumer)} block by pumping, as the pump's own
	 * terminals do, and run the command directly when called from one of the pump's tasks, which is
	 * already inside the realm. {@link #submit(Function)} does not block.
	 *
	 * @param <S> The type of the guarded state
	 */
	// tag::pump-realm[]
	public static final class PumpRealm<S> implements ExclusiveRealm<S> {

		private final S state;
		private final Pump pump;

		PumpRealm(S state, Pump pump) {
			this.state = state;
			this.pump = Objects.requireNonNull(pump, "pump");
		}

		public Pump getPump() {
			return pump;
		}

		/**
		 * Issues a command and returns at once. The stage completes on the pump.
		 */
		public <T> CompletionStage<T> submit(Function<? super S, ? extends T> command) {
			CompletableFuture<T> result = new CompletableFuture<>();
			pump.execute("realm command", () -> {
				try {
					result.complete(command.apply(state));
				} catch (Throwable e) {
					result.completeExceptionally(e); // The command failed, not the pump
				}
			});
			return result;
		}

		@Override
		public <T> T apply(Function<? super S, ? extends T> command) {
			if (pump.isPumping()) {
				return command.apply(state);
			}
			return pump.join(submit(command));
		}

		@Override
		public void execute(Consumer<? super S> command) {
			apply(state -> {
				command.accept(state);
				return null;
			});
		}

	}
	// end::pump-realm[]

}
