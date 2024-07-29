package com.kuriosityrobotics.shuttle;

public final class Instant implements Comparable<Instant> {
	private static final long MILLIS_PER_SECOND = 1_000L;
	private static final long NANOS_PER_MILLI = 1_000_000L;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final long SECONDS_PER_MINUTE = 60L;
	private static final long ELAPSED_NANOS_AT_START = System.nanoTime();
	private static final Instant START_TIME = Instant.ofEpochMillis(System.currentTimeMillis());

	private final long nanos;

	public static Instant now() {
		return new Instant(START_TIME.nanos + System.nanoTime() - ELAPSED_NANOS_AT_START);
	}

	public static Instant ofEpochMillis(long millis) {
		return new Instant(millis * NANOS_PER_MILLI);
	}

	private Instant(long time) {
		nanos = time;
	}

	public Duration sinceStart() {
		return since(START_TIME);
	}

	public boolean isBefore(Instant instant) {
		return nanos < instant.nanos;
	}

	public boolean isAfter(Instant instant) {
		return nanos > instant.nanos;
	}

	public Duration since(Instant time) {
		return Duration.ofNanos(nanos - time.nanos);
	}

	public Duration until(Instant time) {
		return since(time).negated();
	}

	public long toNanos() {
		return nanos;
	}

	public Instant add(Duration duration) {
		return new Instant(nanos + duration.toNanos());
	}

	public Instant subtract(Duration duration) {
		return new Instant(nanos - duration.toNanos());
	}

	public long toEpochMillis() {
		return nanos / NANOS_PER_MILLI;
	}

	public boolean equals(Instant other) {
		return this.nanos == other.nanos;
	}

	@SuppressWarnings("DefaultLocale")
	public String toString() {
		long offset = nanos - START_TIME.nanos;

		long millis = offset / NANOS_PER_MILLI;
		long seconds = millis / MILLIS_PER_SECOND;
		long minutes = seconds / SECONDS_PER_MINUTE;

		return String.format("%d:%d.%3d", minutes, seconds % SECONDS_PER_MINUTE, millis % MILLIS_PER_SECOND);
	}

	@Override
	public int compareTo(Instant other) {
		if (this.equals(other)) return 0;
		if (other.nanos > this.nanos) return -1;
		return 1;
	}
}
