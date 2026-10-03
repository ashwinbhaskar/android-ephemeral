package com.ephemeral

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class InMemoryTest {
    private data class SomeClass(val a: Int, val c: String, val b: Boolean)
    private data class MyClass(val a: String, val b: Float)

    private val clock = FakeClock()

    @Before
    fun setUp() {
        common.nowMillis = clock::now
        InMemory.reset()
    }

    @After
    fun tearDown() {
        common.nowMillis = { System.currentTimeMillis() }
        InMemory.reset()
    }

    @Test
    fun `should be able to put and get values of any type`() {
        val sc = SomeClass(1, "foox", true)
        InMemory.put(key = "foo", value = sc, expireAfter = Duration.ofMinutes(5))

        assertEquals(sc, InMemory.get<SomeClass>("foo"))
        assertEquals(sc, InMemory.get("foo", SomeClass::class))
    }

    @Test
    fun `should return null for a key that was never stored`() {
        assertNull(InMemory.get<String>("never-stored"))
    }

    @Test
    fun `should respect the duration passed`() {
        val mc = MyClass("bax", 1.1f)
        InMemory.put(key = "zoox", value = mc, expireAfter = Duration.ofSeconds(2))

        clock.advance(Duration.ofMillis(1999))
        assertEquals(mc, InMemory.get<MyClass>("zoox"))

        clock.advance(Duration.ofMillis(1))
        assertNull(InMemory.get<MyClass>("zoox"))
    }

    @Test
    fun `a zero duration expires immediately`() {
        InMemory.put("now", 1, Duration.ZERO)
        assertNull(InMemory.get<Int>("now"))
    }

    @Test
    fun `should throw a ClassCastException when trying to get a wrong type`() {
        InMemory.put(key = "dune", value = SomeClass(5, "sheesh", false), expireAfter = Duration.ofSeconds(5))
        try {
            InMemory.get<MyClass>("dune")
            fail("Expected a ClassCastException")
        } catch (e: ClassCastException) {
            assertTrue(e.message!!.contains("dune"))
        }
    }

    @Test
    fun `should be able to put and get primitives`() {
        InMemory.put("arrakis", true, Duration.ofMinutes(5))
        InMemory.put("caladan", 42, Duration.ofMinutes(5))
        InMemory.put("giedi", 3L, Duration.ofMinutes(5))

        assertEquals(true, InMemory.get<Boolean>("arrakis"))
        assertEquals(42, InMemory.get<Int>("caladan"))
        assertEquals(3L, InMemory.get<Long>("giedi"))
    }

    @Test
    fun getAndUpdateExpiryIfPresentTest() {
        val mc = MyClass("bax", 1.1f)
        InMemory.put(key = "zoox", value = mc, expireAfter = Duration.ofSeconds(2))

        assertEquals(mc, InMemory.getAndUpdateExpiryIfPresent<MyClass>("zoox", Duration.ofSeconds(5)))

        clock.advance(Duration.ofSeconds(3))
        assertEquals(mc, InMemory.get<MyClass>("zoox"))

        clock.advance(Duration.ofSeconds(2))
        assertNull(InMemory.get<MyClass>("zoox"))
    }

    @Test
    fun `getAndUpdateExpiryIfPresent does not revive an expired value`() {
        InMemory.put(key = "zoox", value = MyClass("bax", 1.1f), expireAfter = Duration.ofSeconds(2))

        clock.advance(Duration.ofHours(1))

        assertNull(InMemory.getAndUpdateExpiryIfPresent<MyClass>("zoox", Duration.ofSeconds(5)))
        assertNull(InMemory.get<MyClass>("zoox"))
        assertEquals(0, InMemory.entryCount())
    }

    @Test
    fun updateValueIfPresentTest() {
        val mc = MyClass("bax", 1.1f)
        InMemory.put(key = "zoox", value = mc, expireAfter = Duration.ofSeconds(2))

        assertTrue(InMemory.updateValueIfPresent<MyClass>("zoox") { it.copy(a = "bax2") })
        assertEquals(MyClass("bax2", 1.1f), InMemory.get<MyClass>("zoox"))

        assertFalse(InMemory.updateValueIfPresent<MyClass>("missing") { it })

        clock.advance(Duration.ofSeconds(2))
        assertNull(InMemory.get<MyClass>("zoox"))
    }

    @Test
    fun `updateValueIfPresent keeps the original expiry`() {
        InMemory.put(key = "zoox", value = 1, expireAfter = Duration.ofSeconds(2))

        clock.advance(Duration.ofSeconds(1))
        assertTrue(InMemory.updateValueIfPresent<Int>("zoox") { it + 1 })

        clock.advance(Duration.ofSeconds(1))
        assertNull(InMemory.get<Int>("zoox"))
    }

    @Test
    fun `updateValueIfPresent does not update an expired value`() {
        InMemory.put(key = "zoox", value = MyClass("bax", 1.1f), expireAfter = Duration.ofSeconds(2))

        clock.advance(Duration.ofSeconds(2))

        assertFalse(InMemory.updateValueIfPresent<MyClass>("zoox") { it.copy(a = "bax2") })
        assertNull(InMemory.get<MyClass>("zoox"))
        assertEquals(0, InMemory.entryCount())
    }

    @Test
    fun `updateValueIfPresent may call back into the store`() {
        InMemory.put(key = "a", value = 1, expireAfter = Duration.ofMinutes(1))
        InMemory.put(key = "b", value = 10, expireAfter = Duration.ofMinutes(1))

        assertTrue(InMemory.updateValueIfPresent<Int>("a") { it + InMemory.get<Int>("b")!! })
        assertEquals(11, InMemory.get<Int>("a"))
    }

    @Test
    fun `getOrPut returns the stored value without computing`() {
        InMemory.put("k", 1, Duration.ofMinutes(1))

        val value = InMemory.getOrPut<Int>("k", Duration.ofMinutes(1)) { fail("should not compute"); 0 }

        assertEquals(1, value)
    }

    @Test
    fun `getOrPut computes and stores a missing value`() {
        var computations = 0

        val first = InMemory.getOrPut<String>("k", Duration.ofSeconds(2)) { computations++; "computed" }
        val second = InMemory.getOrPut<String>("k", Duration.ofSeconds(2)) { computations++; "other" }

        assertEquals("computed", first)
        assertEquals("computed", second)
        assertEquals(1, computations)
        assertEquals("computed", InMemory.get<String>("k"))
    }

    @Test
    fun `getOrPut recomputes once the value has expired`() {
        InMemory.getOrPut<String>("k", Duration.ofSeconds(2)) { "first" }
        clock.advance(Duration.ofSeconds(2))

        assertEquals("second", InMemory.getOrPut<String>("k", Duration.ofSeconds(2)) { "second" })
        assertEquals("second", InMemory.get<String>("k"))
    }

    @Test
    fun `getOrPut throws when the stored value has another type`() {
        InMemory.put("k", "text", Duration.ofMinutes(1))
        try {
            InMemory.getOrPut<Int>("k", Duration.ofMinutes(1)) { 1 }
            fail("Expected a ClassCastException")
        } catch (e: ClassCastException) {
            assertTrue(e.message!!.contains("k"))
        }
    }

    @Test
    fun removeTest() {
        InMemory.put(key = "zoox", value = MyClass("bax", 1.1f), expireAfter = Duration.ofSeconds(2))

        assertTrue(InMemory.remove("zoox"))
        assertFalse(InMemory.remove("zoox"))
    }

    @Test
    fun `remove reports false for an expired value`() {
        InMemory.put(key = "zoox", value = 1, expireAfter = Duration.ofSeconds(2))
        clock.advance(Duration.ofSeconds(2))

        assertFalse(InMemory.remove("zoox"))
        assertEquals(0, InMemory.entryCount())
    }

    @Test
    fun `put sweeps out expired entries that were never read`() {
        InMemory.put("a", 1, Duration.ofSeconds(1))
        InMemory.put("b", 2, Duration.ofSeconds(1))
        InMemory.put("c", 3, Duration.ofMinutes(1))
        assertEquals(3, InMemory.entryCount())

        clock.advance(Duration.ofSeconds(2))
        InMemory.put("d", 4, Duration.ofMinutes(1))

        assertEquals(2, InMemory.entryCount())
        assertEquals(3, InMemory.get<Int>("c"))
        assertEquals(4, InMemory.get<Int>("d"))
    }

    @Test
    fun `put does not sweep more than once per second`() {
        InMemory.put("a", 1, Duration.ofSeconds(1))
        clock.advance(Duration.ofSeconds(1))
        // Same second as the sweep triggered by the first put: no sweep yet.
        clock.advance(Duration.ofMillis(-1))
        InMemory.put("b", 2, Duration.ofMinutes(1))
        assertEquals(2, InMemory.entryCount())

        clock.advance(Duration.ofMillis(1))
        InMemory.put("c", 3, Duration.ofMinutes(1))
        assertEquals(2, InMemory.entryCount())
    }

    @Test
    fun `purgeExpired drops every expired entry`() {
        InMemory.put("a", 1, Duration.ofSeconds(1))
        InMemory.put("b", 2, Duration.ofMinutes(1))
        clock.advance(Duration.ofSeconds(1))

        InMemory.purgeExpired()

        assertEquals(1, InMemory.entryCount())
        assertEquals(2, InMemory.get<Int>("b"))
    }

    @Test
    fun `concurrent access from many threads does not corrupt the store`() {
        common.nowMillis = { System.currentTimeMillis() }
        val threads = 8
        val iterations = 5_000
        val keys = (0 until 16).map { "k$it" }
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failure = AtomicReference<Throwable>()

        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    repeat(iterations) { i ->
                        val key = keys[(i + t) % keys.size]
                        when (i % 4) {
                            0 -> InMemory.put(key, i, Duration.ofMinutes(1))
                            1 -> InMemory.get<Int>(key)
                            2 -> InMemory.updateValueIfPresent<Int>(key) { it + 1 }
                            else -> InMemory.remove(key)
                        }
                    }
                } catch (e: Throwable) {
                    failure.compareAndSet(null, e)
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue("workers did not finish", done.await(60, TimeUnit.SECONDS))
        pool.shutdown()

        failure.get()?.let { throw AssertionError("worker threw", it) }
        keys.forEach { key -> InMemory.get<Int>(key) }
    }

    private class FakeClock(private var nowMillis: Long = 1_700_000_000_000L) {
        fun now(): Long = nowMillis
        fun advance(by: Duration) {
            nowMillis += by.toMillis()
        }
    }
}
