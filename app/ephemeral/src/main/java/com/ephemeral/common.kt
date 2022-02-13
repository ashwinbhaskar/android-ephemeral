package com.ephemeral

import arrow.core.*
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal object common {

    data class Value<out T>(val v: T, val expiry: LocalDateTime)

    private fun now(): LocalDateTime = LocalDateTime.now()

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun format(ldt: LocalDateTime): String =
        dateTimeFormatter.format(ldt)

    fun expiry(duration: Duration): LocalDateTime =
        now().plusNanos(duration.toNanos())

    fun expiryStr(duration: Duration): String =
        dateTimeFormatter.format(expiry(duration))

    fun hasExpired(expiry: LocalDateTime): Boolean =
        now().isAfter(expiry)

    fun hasExpired(expiryStr: String): Boolean =
        hasExpired(LocalDateTime.parse(expiryStr, dateTimeFormatter))

    fun <T: Any>tryCast(value: common.Value<*>, clazz: KClass<T>): Either<CastError, Option<T>> =
        try {
            clazz.cast(value.v).some().right()
        } catch (e: ClassCastException) {
            CastError(e.message ?: "").left()
        }
}

data class CastError(val msg: String)

fun <T>Either<CastError, T>.unsafe(): T {
    return when(this) {
        is Either.Left -> throw ClassCastException(this.value.msg)
        is Either.Right -> this.value
    }
}
