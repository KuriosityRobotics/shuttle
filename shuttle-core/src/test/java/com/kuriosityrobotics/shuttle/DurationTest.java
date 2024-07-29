package com.kuriosityrobotics.shuttle;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

// class based on assumption that Instant works
class DurationTest {
	// if this one fails then there is no saving us
	@Test
	public void initializerTest() {
		Instant i = Instant.now();
		Duration d = Duration.ofNanos(i.toNanos());
		assertEquals(d.toNanos(), i.toNanos());
	}

	@RepeatedTest(10)
	public void testToMillis() {
		Instant i = Instant.now();
		Duration d = Duration.ofNanos(i.toNanos());
		assertEquals(i.toEpochMillis(), d.toMillis());
	}

	@Test
	public void testCompare() throws InterruptedException {
		Instant i = Instant.now();
		Thread.sleep(200);
		Duration d = Instant.now().since(i);
		Thread.sleep(100);
		Duration d2 = Instant.now().since(i);

		assertEquals(0, d.compareTo(d));
		assertTrue(d.compareTo(d2) < 0);
		assertTrue(d2.compareTo(d) > 0);

		assertEquals(d, d);
		assertEquals(d2, d2);
		assertNotEquals(d, d2);
		assertNotEquals(d2, d);
	}
}