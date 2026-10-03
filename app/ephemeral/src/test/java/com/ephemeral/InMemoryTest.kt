package com.ephemeral

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Duration

class InMemoryTest {
    private data class SomeClass(val a: Int, val c: String, val b: Boolean)
    private data class MyClass(val a: String, val b: Float)

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

        assertEquals(mc, InMemory.get<MyClass>("zoox"))

        Thread.sleep(2001)

        assertNull(InMemory.get<MyClass>("zoox"))
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

        Thread.sleep(3000)

        assertEquals(mc, InMemory.get<MyClass>("zoox"))

        Thread.sleep(2001)

        assertNull(InMemory.get<MyClass>("zoox"))
    }

    @Test
    fun updateValueIfPresentTest() {
        val mc = MyClass("bax", 1.1f)
        InMemory.put(key = "zoox", value = mc, expireAfter = Duration.ofSeconds(2))

        assertTrue(InMemory.updateValueIfPresent<MyClass>("zoox") { it.copy(a = "bax2") })
        assertEquals(MyClass("bax2", 1.1f), InMemory.get<MyClass>("zoox"))

        assertFalse(InMemory.updateValueIfPresent<MyClass>("missing") { it })

        Thread.sleep(2001)

        assertNull(InMemory.get<MyClass>("zoox"))
    }

    @Test
    fun removeTest() {
        InMemory.put(key = "zoox", value = MyClass("bax", 1.1f), expireAfter = Duration.ofSeconds(2))

        assertTrue(InMemory.remove("zoox"))
        assertFalse(InMemory.remove("zoox"))
    }
}
