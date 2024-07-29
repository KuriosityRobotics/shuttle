package com.kuriosityrobotics.shuttle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

public class HardwareTaskScopeTest {
	@Test
	void testHardwareTaskScope() {
		// This is not how you're meant to use HardwareTaskScope, it's jut a test case
		HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open();
		assertNotNull(scope);
		assertFalse(scope.isShutdown());
		scope.shutdown();
		assertTrue(scope.isShutdown());
		assertTimeout(Duration.ofMillis(100), scope::join);
		assertDoesNotThrow(scope::close);
	}

	@Test
	void testNotJoinedException() throws InterruptedException {
		// This is not how you're meant to use HardwareTaskScope, it's just a test case
		HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open();
		scope.fork(() -> {});
		Thread.sleep(100); // wait for thread completion
		assertThrows(IllegalStateException.class, scope::close); // it closes, but it throws an ISE because you didn't join
		assertTrue(scope.isShutdown());

		scope = HardwareTaskScope.open();
		scope.fork(() -> {});
		Thread.sleep(100); // wait for thread completion
		scope.join();
		assertDoesNotThrow(scope::close); // does not throw, because you properly joined
		assertTrue(scope.isShutdown());
	}

	@Test
	void testForkJoin() throws InterruptedException {
		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() -> {
				throw new IllegalStateException("test");
			});

			assertThrows(IllegalStateException.class, scope::join);
		}

		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() -> {
				throw new InterruptedException("test");
			});

			assertThrows(InterruptedException.class, scope::join);
		}

		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() -> {
				throw new InternalError("test");
			});

			assertThrows(InternalError.class, scope::join);
		}

		try (HardwareTaskScope<IOException> scope = HardwareTaskScope.open(IOException.class)) {
			scope.fork(() -> {
				throw new IOException("test");
			});

			assertThrows(IOException.class, scope::join);
		}
	}

	@Test
	void testMainThreadInterruptedException() throws InterruptedException {
		final Thread mainThread = Thread.currentThread();
		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() -> {
				Thread.sleep(500);
				mainThread.interrupt();
			});

			scope.fork(() ->
				assertTimeout(Duration.ofMillis(1000), () -> Thread.sleep(10000)) // should get cancelled
			);

			assertThrows(InterruptedException.class, scope::join);
		}
	}

	@Test
	void testCancellationOnFailure() throws InterruptedException {
		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() -> {
				throw new IllegalStateException("test");
			});

			scope.fork(() ->
					assertTimeout(Duration.ofMillis(1000), () -> Thread.sleep(10000)) // should get cancelled
			);

			Thread.sleep(1000); // wait for thread completion
			assertTrue(scope.isShutdown());
			assertThrows(IllegalStateException.class, scope::join);
		}

		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() -> {
				throw new InterruptedException("test");
			});

			scope.fork(() ->
					assertTimeout(Duration.ofMillis(1000), () -> Thread.sleep(10000)) // should get cancelled
			);

			assertThrows(InterruptedException.class, scope::join);
		}

		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() ->
					assertTimeout(Duration.ofMillis(1000), () -> Thread.sleep(10000)) // should get cancelled
			);

			scope.shutdown();
			assertDoesNotThrow(scope::join); // exceptions don't propagate if shutdown prematurely
		}

		try (HardwareTaskScope<InterruptedException> scope = HardwareTaskScope.open()) {
			scope.fork(() -> Thread.sleep(100));
			scope.fork(() -> Thread.sleep(100));

			assertDoesNotThrow(scope::join);
		}
	}
}
