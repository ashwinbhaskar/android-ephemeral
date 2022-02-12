package com.ephemeral

import arrow.core.Either
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
        InMemory.put(key = "foo", value = sc, expireAfter = duration)
        when(val result = InMemory.get("foo", SomeClass::class).unsafe()) {
            is None -> Assert.fail("Result should not be empty")
            is Some -> assert(result.value == sc)
        }
    }

    @Test
    fun `should respect the duration passed`(){
        val twoSeconds = Duration.ofSeconds(2L)
        val mc = MyClass("bax", 1.1f)

        InMemory.put(key = "zoox", value = mc, expireAfter = twoSeconds)

        when(val result = InMemory.get("zoox", MyClass::class).unsafe()) {
            is None -> Assert.fail("Result should not be empty")
            is Some -> assert(result.value == mc)
        }

        Thread.sleep(2001)

        when(InMemory.get("zoox", MyClass::class).unsafe()) {
            is None -> assert(true)
            is Some -> Assert.fail("Result should be empty as it has expired")
        }
    }

    @Test
    fun `should return a cast error when trying to get a wrong type`() {
        val sc = SomeClass(5, "sheesh", false)
        InMemory.put(key = "dune", value = sc, expireAfter = Duration.ofSeconds(5))
        val result = InMemory.get("dune", MyClass::class)
        assert(result.isLeft())
    }

    @Test
    fun `should be able to put and get primitives`() {
        val duration = Duration.ofMinutes(5)

        InMemory.put("arrakis", true, duration)

        val result = InMemory.get("arrakis", Boolean::class)

        assert(result.isRight())
    }

    @Test
    fun getAndUpdateExpiryIfPresentTest() {
        val twoSeconds = Duration.ofSeconds(2L)
        val mc = MyClass("bax", 1.1f)

        InMemory.put(key = "zoox", value = mc, expireAfter = twoSeconds)


        when(val result = InMemory.getAndUpdateExpiryIfPresent("zoox", Duration.ofSeconds(5), MyClass::class).unsafe()) {
            is None -> Assert.fail("Result should not be empty")
            is Some -> assert(result.value == mc)
        }

        Thread.sleep(3000)

        when(val result = InMemory.get("zoox", MyClass::class).unsafe()) {
            is None -> Assert.fail("Result should not be empty as the expiry is updated")
            is Some -> assert(result.value == mc)
        }

        Thread.sleep(2001)

        val result = InMemory.get("zoox", MyClass::class).unsafe()

        assert(result.isEmpty())

    }

    @Test
    fun updateValueIfPresentTest() {
        val twoSeconds = Duration.ofSeconds(2L)
        val mc = MyClass("bax", 1.1f)

        InMemory.put(key = "zoox", value = mc, expireAfter = twoSeconds)

        val didUpdate = InMemory.updateValueIfPresent(key = "zoox", updateFunc = {it.copy(a = "bax2")}, MyClass::class)

        assert(didUpdate.isRight())
        Assert.assertEquals(Either.Right(true), didUpdate)

        when(val result = InMemory.get(key = "zoox", MyClass::class).unsafe()) {
            is None -> Assert.fail("Result should not be empty")
            is Some -> assert(result.value == MyClass("bax2", 1.1f))
        }

        Thread.sleep(2001)

        val result = InMemory.get(key = "zoox", SomeClass::class).unsafe()

        assert(result.isEmpty())

    }

    @Test
    fun removeTest() {
        val twoSeconds = Duration.ofSeconds(2L)
        val mc = MyClass("bax", 1.1f)

        InMemory.put(key = "zoox", value = mc, expireAfter = twoSeconds)

        val isRemoved = InMemory.remove("zoox")
        assert(isRemoved)

        val isRemovedAgain = InMemory.remove("zoox")
        assert(isRemovedAgain.not())
    }
}