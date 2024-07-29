package com.kuriosityrobotics.shuttle;

public final class Duration implements Comparable<Duration> {
	private static final long MILLIS_PER_SECOND = 1_000L;
	private static final long NANOS_PER_MILLI = 1_000_000L;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private final long nanos;

	private Duration(long time) {
		nanos = time;
	}

	public static Duration ofNanos(long nanos) {
		return new Duration(nanos);
	}

	public static Duration ofMillis(long millis) {
		return new Duration(millis * NANOS_PER_MILLI);
	}

	public static Duration ofSeconds(double seconds) {
		return ofNanos((long) (seconds * NANOS_PER_SECOND));
	}

	public static Duration between(Instant now, Instant deadline) {
		return new Duration(deadline.toNanos() - now.toNanos());
	}

	public Duration negated() {
		return new Duration(-nanos);
	}

	public long toNanos() {
		return nanos;
	}

	public long toMillis() {
		return nanos / NANOS_PER_MILLI;
	}

	public double toSeconds() {
		return nanos / (double) NANOS_PER_SECOND;
	}

	public boolean isGreaterThan(Duration duration) {
		return nanos > duration.nanos;
	}

	public boolean isLessThan(Duration duration) {
		return nanos < duration.nanos;
	}

	public boolean equals(Duration other) {
		return this.nanos == other.nanos;
	}

	@Override
	public int compareTo(Duration other) {
		if (this.equals(other)) return 0;
		if (other.nanos > this.nanos) return -1;
		return 1;
	}
}
