package com.ephemeral

import android.media.audiofx.BassBoost
import arrow.core.Either
import arrow.core.Either.*
import com.google.gson.Gson
import org.junit.Assert
import org.junit.Test
import java.time.LocalDateTime

class CommonTest {

    private data class BasicClass(val str: String, val dub: Double, val bool: Boolean, val lng: Long)
    private data class BasicClass1(val st: String, val i: Int)

    @Test
    fun `serialize and deserialize should work correctly for basic class`() {
        val basicClass = BasicClass("foo", 1.24, true, 12343453434343L)
        val time = LocalDateTime.now().plusHours(2)

        val str = common.serialize(common.Value(basicClass, time), BasicClass::class)

        val deserializedValue = common.deserialize(str, BasicClass::class)

        assert(deserializedValue.isRight())
        assert(Right(basicClass) == deserializedValue.map { it.v })
        assert(Right(time) == deserializedValue.map { it.expiry })
    }

    @Test
    fun `deserialize should give CastError when the wrong class is given`() {
        val basicClass = BasicClass("foo", 1.24, true, 12343453434343L)
        val time = LocalDateTime.now().plusHours(2)

        val str = common.serialize(common.Value(basicClass, time), BasicClass::class)

        val deserializedValue = common.deserialize(str, BasicClass1::class)

        assert(deserializedValue.isLeft())

        Assert.assertEquals(Left(CastError("cannot cast com.ephemeral.CommonTest.BasicClass to com.ephemeral.CommonTest.BasicClass1")), deserializedValue)
    }


}