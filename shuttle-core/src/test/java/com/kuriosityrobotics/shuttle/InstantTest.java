package com.kuriosityrobotics.shuttle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.*;

class InstantTest {
	private static final long NS_TO_S = 1_000_000_000;
	private static final long NS_TO_MS = 1_000_000;

	@RepeatedTest(10)
	void testInstantNow() throws InterruptedException {
		Instant begin = Instant.now();
		Thread.sleep(100);
		Instant end = Instant.now();
		assertTrue(end.toNanos() - begin.toNanos() - NS_TO_S < 5 * NS_TO_MS);
	}

	@RepeatedTest(10)
	void testInstantUntil() throws InterruptedException {
		Instant instant = Instant.now();
		Thread.sleep(100);
		Instant end = Instant.now();
		assertTrue(instant.until(end).toNanos() < NS_TO_S + (5 * NS_TO_MS));
	}

	@RepeatedTest(10)
	void testInstantAdd() throws InterruptedException {
		Instant instant = Instant.now();
		Thread.sleep(100);
		Instant end = Instant.now();
		assertTrue(Math.abs(end.toNanos() - instant.add(Duration.ofNanos(100 * NS_TO_MS)).toNanos()) < 10 * NS_TO_MS);
	}

	@Test
	void testInstantCompare() throws InterruptedException {
		Instant start = Instant.now();
		Thread.sleep(100);
		Instant end = Instant.now();
		assertTrue(start.compareTo(end) < 0);
		assertTrue(end.compareTo(start) > 0);
		assertEquals(0, start.compareTo(start));

		assertEquals(start, start);
		assertEquals(end, end);
	}
}
