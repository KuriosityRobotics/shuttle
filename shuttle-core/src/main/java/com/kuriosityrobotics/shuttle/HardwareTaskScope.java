package com.kuriosityrobotics.shuttle;

import static com.kuriosityrobotics.shuttle.StructuredTaskScope.Subtask.State.FAILED;

import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A subclass of {@link StructuredTaskScope} that requires all subtasks to succeed.
 * This scope will shut down if any subtasks fail, and the exception will be rethrown
 * in the main thread when {@link #join} is called.
 * <br>
 * This scope allows subtasks to throw {@link InterruptedException} as well as an additional exception {@code <E>}.
 * @param <E> the exception thrown by the scope
 */
public class HardwareTaskScope<E extends Exception> extends StructuredTaskScope<Object> {
	// either E, InterruptedException, RuntimeException or Error
	private final AtomicReference<Throwable> firstException = new AtomicReference<>();

	private HardwareTaskScope() {}

	@Override
	protected void handleComplete(Subtask<?> subtask) {
		super.handleComplete(subtask);

		if (subtask.state() == FAILED) {
			firstException.compareAndSet(null, subtask.exception());
			shutdown();
		}
	}

	public Subtask<Void> fork(HardwareTask<? extends E> task) {
		return super.forkInner(() -> {
			task.run();
			return null;
		});
	}

	public <T> Subtask<T> fork(HardwareSupplier<T, ? extends E> task) {
		return super.forkInner(task::supply);
	}

	private void throwIfPresent(Throwable e) throws InterruptedException, E {
		if (e != null) {
			if (e instanceof InterruptedException) {
				throw (InterruptedException) e;
			}

			if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			}

			if (e instanceof Error) {
				throw (Error) e;
			}

			throw (E) e;
		}
	}

	public HardwareTaskScope<E> join() throws InterruptedException, E {
		super.joinInner();

		var e = firstException.get();
		throwIfPresent(e);

		return this;
	}

	public HardwareTaskScope<E> joinUntil(Instant deadline) throws InterruptedException, TimeoutException, E {
		super.joinUntilInner(deadline);

		var e = firstException.get();
		throwIfPresent(e);

		return this;
	}

	public static HardwareTaskScope<InterruptedException> open() {
		return new HardwareTaskScope<>();
	}

	public static <E extends Exception> HardwareTaskScope<E> open(Class<E> clazz) {
		Objects.requireNonNull(clazz);
		return new HardwareTaskScope<>();
	}

	public interface HardwareTask<E extends Exception> {
		void run() throws E, InterruptedException;
	}

	public interface HardwareSupplier<T, E extends Exception> {
		T supply() throws E, InterruptedException;
	}
}
