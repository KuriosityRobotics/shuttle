package com.kuriosityrobotics.shuttle;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A lock that can be preempted by another thread.
 * If a lock is 'preempted', the holder of the lock will be interrupted.
 */
public class PreemptibleLock implements Lock {
	private final ReentrantLock outerLock; // waiters queue in order to lock; nothing else interferes
	private final ReentrantLock acquisitionLock = new ReentrantLock(); // minimally held by owner when unlocking, or waiter when locking
	private final Condition acquisitionCondition = acquisitionLock.newCondition();
	private Thread owner = null; // guarded by acquisitionLock
	private int holdCount = 0; // guarded by acquisitionLock

	public PreemptibleLock() {
		outerLock = new ReentrantLock(); // non-fair by default
	}

	public PreemptibleLock(boolean fair) {
		outerLock = new ReentrantLock(fair);
	}

	public boolean isLocked() {
		acquisitionLock.lock();
		try {
			return owner != null;
		} finally {
			acquisitionLock.unlock();
		}
	}

	@Override
	public void unlock() {
		acquisitionLock.lock();
		try {
			ensureOwner();

			if (--holdCount == 0) {
				owner = null;
				acquisitionCondition.signalAll();
			}
		} finally {
			acquisitionLock.unlock();
		}
	}

	/**
	 * @return whether the attempt was reentrant
	 */
	private boolean handleReentrantLock() {
		acquisitionLock.lock();
		try {
			if (owner == Thread.currentThread()) {
				holdCount++;
				return true;
			}
			return false;
		} finally {
			acquisitionLock.unlock();
		}
	}

	/** Guarded by acquisitionLock */
	private void setToCurrentOwner() {
		owner = Thread.currentThread();
		holdCount++;
	}

	/** Guarded by acquisitionLock */
	private void ensureOwner() {
		if (owner != Thread.currentThread())
			throw new IllegalMonitorStateException("Calling thread does not hold the lock");
	}

	@Override
	public void lock() {
		if (handleReentrantLock())
			return;

		outerLock.lock();
		try {
			acquisitionLock.lock();
			try {
				if (owner != null) {
					owner.interrupt();

					do
						acquisitionCondition.awaitUninterruptibly();
					while (owner != null);
				}

				// acquired
				owner = Thread.currentThread();
				holdCount = 1;
			} finally {
				acquisitionLock.unlock();
			}
		} finally {
			outerLock.unlock();
		}
	}

	@Override
	public void lockInterruptibly() throws InterruptedException {
		if (Thread.interrupted())
			throw new InterruptedException();

		if (handleReentrantLock())
			return;

		outerLock.lockInterruptibly();
		try {
			acquisitionLock.lockInterruptibly();
			try {
				if (owner != null) {
					owner.interrupt();

					do
						acquisitionCondition.await();
					while (owner != null);
				}

				// acquired
				owner = Thread.currentThread();
				holdCount = 1;
			} finally {
				acquisitionLock.unlock();
			}
		} finally {
			outerLock.unlock();
		}
	}

	/**
	 * Tries to acquire the lock with minimum contention,
	 * if it is not held by another thread and there are no other waiters.
	 * This method does not preempt (interrupt) any previous lock holders.
	 *
	 * @return true if the lock was acquired, false otherwise
	 */
	@Override
	public boolean tryLock() {
		if (handleReentrantLock())
			return true;

		if (!outerLock.tryLock()) return false; // there are already waiters
		try {
			acquisitionLock.lock();
			try {
				if (owner != null)
					return false;

				// acquired
				owner = Thread.currentThread();
				holdCount = 1;
				return true;
			} finally {
				acquisitionLock.unlock();
			}
		} finally {
			outerLock.unlock();
		}
	}

	/**
	 * Tries to acquire the lock.
	 * This method may preempt (interrupt) any previous lock holders,
	 * but it is not guaranteed that the lock will be acquired after the interrupt.
	 *
	 * @return true if the lock was acquired, false otherwise
	 */
	@Override
	public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
		Date deadline = new Date(System.currentTimeMillis() + unit.toMillis(time));

		if (handleReentrantLock()) // minimal contention, don't count
			return true;

		if (!outerLock.tryLock(time, unit)) return false;
		try {
			acquisitionLock.lock(); // minimal contention, don't count
			try {
				if (owner != null) {
					owner.interrupt();

					do
						if (!acquisitionCondition.awaitUntil(deadline))
							return false;
					while (owner != null);
				}

				// acquired
				owner = Thread.currentThread();
				holdCount = 1;
				return true;
			} finally {
				acquisitionLock.unlock();
			}
		} finally {
			outerLock.unlock();
		}
	}

	@Override
	public Condition newCondition() {
		throw new UnsupportedOperationException("Conditions are not supported");
	}
}