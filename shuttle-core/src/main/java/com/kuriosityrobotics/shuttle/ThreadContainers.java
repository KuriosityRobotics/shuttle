package com.kuriosityrobotics.shuttle;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * This class consists exclusively of static methods to support debugging and
 * monitoring of threads.
 */
class ThreadContainers {
	private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

	// the set of thread containers registered with this class
	private static final Set<WeakReference<ThreadContainer>> CONTAINER_REGISTRY = ConcurrentHashMap.newKeySet();
	private static final ReferenceQueue<Object> QUEUE = new ReferenceQueue<>();

	private ThreadContainers() { }

	/**
	 * Expunge stale entries from the container registry.
	 */
	private static void expungeStaleEntries() {
		Object key;
		while ((key = QUEUE.poll()) != null) {
			CONTAINER_REGISTRY.remove(key);
		}
	}

	/**
	 * Registers a thread container to be tracked this class, returning a key
	 * that is used to remove it from the registry.
	 */
	public static Object registerContainer(ThreadContainer container) {
		expungeStaleEntries();
		var ref = new WeakReference<>(container);
		CONTAINER_REGISTRY.add(ref);
		return ref;
	}

	/**
	 * Removes a thread container from being tracked by specifying the key
	 * returned when the thread container was registered.
	 */
	public static void deregisterContainer(Object key) {
		assert key instanceof WeakReference;
		CONTAINER_REGISTRY.remove(key);
	}

	/**
	 * Returns the root thread container.
	 */
	public static ThreadContainer root() {
		return ThreadContainers.RootContainer.INSTANCE;
	}

	/**
	 * Returns the parent of the given thread container.
	 *
	 * If the container has an owner then its parent is the enclosing container when
	 * nested, or the container that the owner is in, when not nested.
	 *
	 * If the container does not have an owner then the root container is returned,
	 * or null if called with the root container.
	 */
	static ThreadContainer parent(ThreadContainer container) {
		Thread owner = container.owner();
		if (owner != null) {
			ThreadContainer parent = container.enclosingScope(ThreadContainer.class);
			if (parent != null)
				return parent;
			if ((parent = container(owner)) != null)
				return parent;
		}
		ThreadContainer root = root();
		return (container != root) ? root : null;
	}

	private static <T> Stream<T> stream(Optional<T> opt) {
		return opt.map(Stream::of).orElse(Stream.empty());
	}

	/**
	 * Returns given thread container's "children".
	 */
	static Stream<ThreadContainer> children(ThreadContainer container) {
		// children of registered containers
		Stream<ThreadContainer> s1 = CONTAINER_REGISTRY.stream()
				.map(WeakReference::get)
				.filter(c -> c != null && c.parent() == container);

		// container may enclose another container
		Stream<ThreadContainer> s2 = Stream.empty();
		if (container.owner() != null) {
			ThreadContainer next = next(container);
			if (next != null)
				s2 = Stream.of(next);
		}

		// the top-most container owned by the threads in the container
		Stream<ThreadContainer> s3 = container.threads()
				.map(t -> Optional.ofNullable(top(t)))
				.flatMap(ThreadContainers::stream);

		return Stream.concat(s1, Stream.concat(s2, s3));
	}

	/**
	 * Returns the thread container that the given Thread is in or the root
	 * container if not started in a container.
	 * @throws IllegalStateException if the thread has not been started
	 */
	public static ThreadContainer container(Thread thread) {
		// thread container is set when the thread is started
		if (thread.isAlive() || thread.getState() == Thread.State.TERMINATED) {
			ThreadContainer container = JLA.threadContainer(thread);
			return (container != null) ? container : root();
		} else {
			throw new IllegalStateException("Thread not started");
		}
	}

	/**
	 * Returns the top-most thread container owned by the given thread.
	 */
	private static ThreadContainer top(Thread thread) {
		StackableScope current = JLA.headStackableScope(thread);
		ThreadContainer top = null;
		while (current != null) {
			if (current instanceof ThreadContainer) {
				top = (ThreadContainer) current;
			}
			current = current.previous();
		}
		return top;
	}

	/**
	 * Returns the thread container that the given thread container encloses.
	 */
	private static ThreadContainer next(ThreadContainer container) {
		StackableScope current = JLA.headStackableScope(container.owner());
		if (current != null) {
			ThreadContainer next = null;
			while (current != null) {
				if (current == container) {
					return next;
				} else if (current instanceof ThreadContainer) {
					next = (ThreadContainer) current;
				}
				current = current.previous();
			}
		}
		return null;
	}

	/**
	 * Root container that "contains" all platform threads not started in a
	 * container plus some (or all) virtual threads that are started directly
	 * with the Thread API.
	 */
	private static abstract class RootContainer extends ThreadContainer {
		static final ThreadContainers.RootContainer INSTANCE= new ThreadContainers.RootContainer.TrackingRootContainer();

		protected RootContainer() {
			super(true);
		}
		@Override
		public ThreadContainer parent() {
			return null;
		}
		@Override
		public String toString() {
			return "<root>";
		}
		@Override
		public StackableScope previous() {
			return null;
		}

		/**
		 * Root container that tracks all threads.
		 */
		private static class TrackingRootContainer extends ThreadContainers.RootContainer {
			private static final Set<Thread> THREADS = ConcurrentHashMap.newKeySet();
			@Override
			public void onStart(Thread thread) {
				THREADS.add(thread);
			}
			@Override
			public void onExit(Thread thread) {
				THREADS.remove(thread);
			}
			@Override
			public long threadCount() {
				return THREADS.size();
			}
			@Override
			public Stream<Thread> threads() {
				return THREADS.stream();
			}
		}
	}
}
