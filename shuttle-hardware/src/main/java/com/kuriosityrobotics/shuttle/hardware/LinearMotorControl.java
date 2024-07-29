package com.kuriosityrobotics.shuttle.hardware;

import com.kuriosityrobotics.shuttle.PreemptibleLock;
import com.kuriosityrobotics.shuttle.Instant;
import com.kuriosityrobotics.shuttle.Duration;

import java.util.concurrent.TimeoutException;

/**
 * A synchronous abstraction for controlling a one-dimensional motor.
 * This class provides a synchronous wrapper for controlling an abstract device that can implement
 * {@link #setTargetPositionMeters0(double)} and {@link #isBusy()}.
 * <p>
 * This class is meant to wrap {@link com.qualcomm.robotcore.hardware.DcMotor}'s builtin
 * PID, which runs asynchronously on the embedded controller.
 */
public abstract class LinearMotorControl implements MetricPositionSensor, MetricVelocitySensor {
	protected final PreemptibleLock lock = new PreemptibleLock();
	private final Duration timeout;

	protected LinearMotorControl() {
		this.timeout = Duration.ofSeconds(5);
	}

	/**
	 * @param timeout The maximum time to wait for the motor to reach its target position.
	 *                After this time is elapsed, execution will continue, regardless of whether the encoder
	 *                has reached its target position.
	 */
	protected LinearMotorControl(Duration timeout) {
		this.timeout = timeout;
	}

	/**
	 * Moves (in a blocking way, with timeout handling) motor to the `position` (in meters)
	 *
	 * @param position the position, in meters, that the motor should try to go to.
	 * @throws InterruptedException
	 */
	public void goToPosition(double position) throws InterruptedException, TimeoutException {
		lock.lockInterruptibly();
		try {
			setTargetPositionMeters0(position);

			Instant startTime = Instant.now();
			while (isBusy()) {
				if (Instant.now().since(startTime).isGreaterThan(timeout))
					throw new TimeoutException("Timed out: did not finish within " + timeout.toSeconds() + " seconds.");

				idle();
			}

		} finally {
			lock.unlock();
		}
	}

	/**
	 * Waits until the motor's busyness might have changed.  By default, this method waits for 30ms.
	 *
	 * @throws InterruptedException if the thread is interrupted while waiting.
	 */
	protected void idle() throws InterruptedException {
		Thread.sleep(30);
	}

	/**
	 * Returns true if the motor is not at its target position.
	 *
	 * @return whether the motor is busy.
	 */
	protected abstract boolean isBusy();

	/**
	 * Sets the target position of the motor, in meters.
	 *
	 * @param position the position, in metres, that the motor should try to go to.
	 */
	protected abstract void setTargetPositionMeters0(double position);
}