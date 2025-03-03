package com.kuriosityrobotics.shuttle;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;

import static org.junit.jupiter.api.Assertions.*;

class PreemptibleLockTest {

	@Test
	void testLockWhenUnlocked() {
		PreemptibleLock lock = new PreemptibleLock();
		assertFalse(lock.isLocked());
		assertThrows(IllegalMonitorStateException.class, lock::unlock);
		lock.lock();
		assertTrue(lock.isLocked());
		assertDoesNotThrow(lock::unlock);
		assertFalse(lock.isLocked());
		assertThrows(IllegalMonitorStateException.class, lock::unlock);
	}

	@RepeatedTest(10)
	void testLockWhenLockedByAnotherThread() throws InterruptedException {
		PreemptibleLock lock = new PreemptibleLock();
		CountDownLatch latch = new CountDownLatch(1);

		Thread t1 = new Thread(() -> {
			lock.lock();
			try {
				latch.countDown();
				//Hold lock for a while
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// Expected interruption
			} finally {
				lock.unlock();
			}
		});

		Thread t2 = new Thread(() -> {
			lock.lock();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			lock.unlock();
		});

		t1.start();
		latch.await(); // wait for the lock to get acquired
		t2.start(); // should cause t1 to be interrupted

		assertTimeout(Duration.ofMillis(100), () -> t1.join());
		Thread.sleep(100); // Let t2 acquire the lock
		assertTrue(t2.isAlive());
		assertFalse(t1.isAlive());
		assertTrue(lock.isLocked());

		t2.join();

		assertFalse(lock.isLocked());
	}

	@Test
	void testLockUnderHighContention() throws InterruptedException, BrokenBarrierException {
		final int threadCount = 1000;
		final PreemptibleLock lock = new PreemptibleLock();
		final Phaser latch = new Phaser(threadCount + 1);
		class Holder {
			int counter = 0;
		}

		final Holder holder = new Holder();

		// Spawn a bunch of threads that try to lock and increment counter
		for (int i = 0; i < threadCount; i++) {
			new Thread(() -> {
				latch.arriveAndAwaitAdvance(); // Wait for all threads to be ready
				lock.lock();
				try {
					holder.counter++; // Protected by lock
				} finally {
					lock.unlock();
				}
				latch.arrive(); // Wait for all threads to finish
			}).start();
		}

		lock.lock();
		try {
			assertEquals(0, holder.counter);
		} finally {
			lock.unlock();
		}

		latch.arriveAndAwaitAdvance(); // let them start
		latch.arriveAndAwaitAdvance(); // wait for them to finish

		// Check for race conditions or deadlocks
		lock.lock();
		try {
			assertEquals(threadCount, holder.counter);
		} finally {
			lock.unlock();
		}
		assertFalse(lock.isLocked());
	}

	@Test
	void testReentrancy() {
		PreemptibleLock lock = new PreemptibleLock();
		lock.lock();
		assertTrue(lock.isLocked());
		lock.lock();
		assertTrue(lock.isLocked());
		lock.unlock();
		assertTrue(lock.isLocked());
		lock.unlock();
		assertFalse(lock.isLocked());

		assertTrue(lock.tryLock());
		assertTrue(lock.isLocked());
		assertTrue(lock.tryLock());
		assertTrue(lock.isLocked());
		lock.unlock();
		assertTrue(lock.isLocked());
		lock.unlock();
		assertFalse(lock.isLocked());
	}

	@Test
	void testPreemption() throws InterruptedException {
		final int threadCount = 10;
		final PreemptibleLock lock = new PreemptibleLock();
		final Phaser latch = new Phaser(threadCount + 1);

		class Holder {
			int started = 0;
			int successful = 0;
			int interrupted = 0;
		}

		final Holder holder = new Holder();

		ArrayList<Thread> threads = new ArrayList<>(threadCount);
		for (int i = 0; i < threadCount; i++) {
			Thread t = new Thread(() -> {
				try {
					latch.arriveAndAwaitAdvance();

					lock.lock();
					try {
						holder.started++;
						Thread.sleep(1000); // Hold lock for a while
						holder.successful++;
					} catch (InterruptedException e) {
						Thread.sleep(100); // small delay to let other threads pile up
						holder.interrupted++;
					} finally {
						lock.unlock();
					}
					latch.arrive();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

			threads.add(t);
			t.start();
		}

		latch.arriveAndAwaitAdvance(); // let them start

		assertTimeout(Duration.ofMillis(1000 + 100 * threadCount + 1000), () -> {
			latch.arriveAndAwaitAdvance();
		});

		assertTimeout(Duration.ofMillis(100), () -> {
			for (Thread t : threads) {
				t.join();
			}
		});

		assertTimeout(Duration.ofMillis(100), lock::lock);
		assertTrue(lock.isLocked());
		assertEquals(threadCount, holder.started);
		assertEquals(1, holder.successful);
		assertEquals(threadCount - 1, holder.interrupted);
		lock.unlock();
	}

	@Test
	void testTryLockPreemption() throws InterruptedException {
		final PreemptibleLock lock = new PreemptibleLock();
		final Phaser barrier = new Phaser(2);

		Thread t1 = new Thread(() -> {
			try {
				barrier.arriveAndAwaitAdvance();

				lock.lock();
				try {
					barrier.arriveAndAwaitAdvance();
					Thread.sleep(10000);
				} finally {
					lock.unlock();
				}
				barrier.arrive();
			} catch (InterruptedException ignored) {
			}
		});

		t1.start();

		barrier.arriveAndAwaitAdvance(); // wait for thread to start
		barrier.arriveAndAwaitAdvance(); // wait for the lock to be acquired

		assertFalse(lock.tryLock()); // tryLock doesn't preempt
		assertTrue(lock.isLocked());
		assertFalse(t1.isInterrupted());

		assertTrue(lock.tryLock(100, TimeUnit.MILLISECONDS));
		try {
			assertTrue(lock.isLocked());
			assertTimeout(Duration.ofMillis(100), () -> t1.join());
		} finally {
			lock.unlock();
		}

		assertFalse(lock.isLocked());
	}

	@Test
	void testTryLockPreemptionTimeout() throws InterruptedException {
		final PreemptibleLock lock = new PreemptibleLock();
		final Phaser barrier = new Phaser(2);

		Thread t1 = new Thread(() -> {
			try {
				barrier.arriveAndAwaitAdvance();

				lock.lock();
				try {
					barrier.arriveAndAwaitAdvance();
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						Thread.sleep(500); // take too long to clean up
					}
				} finally {
					lock.unlock();
				}
				barrier.arrive();
			} catch (InterruptedException ignored) {
			}
		});

		t1.start();

		barrier.arriveAndAwaitAdvance(); // wait for thread to start
		barrier.arriveAndAwaitAdvance(); // wait for the lock to be acquired

		assertFalse(lock.tryLock()); // tryLock doesn't preempt
		assertTrue(lock.isLocked());
		assertFalse(t1.isInterrupted());

		assertFalse(lock.tryLock(400, TimeUnit.MILLISECONDS)); // 400 ms isn't enough
		assertTrue(lock.tryLock(400, TimeUnit.MILLISECONDS));  // try again
		try {
			assertTrue(lock.isLocked());
			assertTimeout(Duration.ofMillis(100), () -> t1.join());
		} finally {
			lock.unlock();
		}

		assertFalse(lock.isLocked());
	}

	@RepeatedTest(10)
	void testPreemptsExactlyOnce() throws InterruptedException {
		PreemptibleLock lock = new PreemptibleLock();

		Thread t = new Thread(() -> {
			lock.lock();
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				// Expected interruption
			} finally {
				lock.unlock();
			}
		});

		lock.lock();
		try {
			assertFalse(Thread.currentThread().isInterrupted());
			assertDoesNotThrow(lock::lockInterruptibly);
			assertDoesNotThrow(lock::unlock);

			t.start(); // start new thread to preempt
			// wait for preemption
			assertThrows(InterruptedException.class, () -> Thread.sleep(Long.MAX_VALUE));
		} finally {
			lock.unlock();
		}

		assertThrows(IllegalMonitorStateException.class, lock::unlock);
		assertTimeout(Duration.ofMillis(200), () -> lock.tryLock(100, TimeUnit.MILLISECONDS));
		assertTimeout(Duration.ofMillis(100), () -> t.join());
		assertTrue(lock.tryLock());
	}

	@RepeatedTest(10)
	void tryLockThrows() throws InterruptedException {
		PreemptibleLock lock = new PreemptibleLock();

		Thread.currentThread().interrupt();
		assertThrows(InterruptedException.class, lock::lockInterruptibly); // interruptible
		assertFalse(lock.isLocked());
		assertFalse(Thread.currentThread().isInterrupted()); // should clear when thrown

		Thread.currentThread().interrupt();
		assertDoesNotThrow(lock::lock); // uninterruptible
		assertTrue(lock.isLocked());
		assertTrue(Thread.currentThread().isInterrupted()); // unchanged

		assertDoesNotThrow(lock::unlock);
		assertTrue(Thread.currentThread().isInterrupted()); // unchanged
	}

	@RepeatedTest(10)
	void testFairness() throws InterruptedException {
		final int threadCount = 10;
		final PreemptibleLock lock = new PreemptibleLock(true);

		final List<Thread> lockAcquireOrder = new ArrayList<>(threadCount); // guarded by lock
		final List<Thread> threadStartOrder = new ArrayList<>(threadCount);

		lock.lock();
		try {
			for (int i = 0; i < threadCount; i++) {
				Thread t = new Thread(() -> {
					try {
						lock.lock();
						try {
							lockAcquireOrder.add(Thread.currentThread());
						} finally {
							lock.unlock();
						}
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				});

				threadStartOrder.add(t);
				t.start();

				// wait until contended
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// do nothing
				}
			}
		} finally {
			lock.unlock();
		}

		assertTimeout(Duration.ofMillis(100), () -> {
			for (Thread t : threadStartOrder) {
				t.join();
			}
		});

		assertFalse(lock.isLocked());
		assertFalse(Thread.currentThread().isInterrupted());

		// compare results
		lock.lock();
		try {
			assertEquals(threadStartOrder, lockAcquireOrder);
		} finally {
			lock.unlock();
		}
	}
}
