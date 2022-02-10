package com.ephemeral

import arrow.core.None
import arrow.core.Some
import org.junit.Test
import org.junit.Assert
import java.time.Duration

class InMemoryTest {
    private data class SomeClass(val a: Int, val c: String, val b: Boolean)
    private data class MyClass(val a: String, val b: Float)

    @Test
    fun `should be able to put and get values of any type`() {
        val duration = Duration.ofMinutes(5)

        val sc = SomeClass(1, "foox", true)
        inmemory.put(key = "foo", value = sc, expireAfter = duration)
        when(val result = inmemory.getUnsafe("foo", SomeClass::class.java)) {
            is None -> Assert.fail("Result should not be empty")
            is Some -> assert(result.value == sc)
        }
    }

    @Test
    fun `should respect the duration passed`(){
        val twoSeconds = Duration.ofSeconds(2L)
        val mc = MyClass("bax", 1.1f)

        inmemory.put(key = "zoox", value = mc, expireAfter = twoSeconds)

        when(val result = inmemory.getUnsafe("zoox", MyClass::class.java)) {
            is None -> Assert.fail("Result should not be empty")
            is Some -> assert(result.value == mc)
        }

        Thread.sleep(2001)

        when(inmemory.getUnsafe("zoox", MyClass::class.java)) {
            is None -> assert(true)
            is Some -> Assert.fail("Result should be empty as it has expired")
        }
    }

    @Test
    fun `should return a cast error when trying to get a wrong type`() {
        val sc = SomeClass(5, "sheesh", false)
        inmemory.put(key = "dune", value = sc, expireAfter = Duration.ofSeconds(5))
        val result = inmemory.get("dune", MyClass::class.java)
        assert(result.isLeft())
    }
}