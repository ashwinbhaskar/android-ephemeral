package com.ephemeral

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

    /**
     * Casts a stored value to [clazz], or throws a [ClassCastException] whose message
     * names the key, the stored type and the requested type.
     */
    fun <T : Any> cast(key: String, value: Any?, clazz: KClass<T>): T {
        if (!clazz.isInstance(value)) {
            throw ClassCastException(
                "Value stored under key '$key' is of type ${value?.let { it::class.qualifiedName }}, " +
                    "not ${clazz.qualifiedName}"
            )
        }
        return clazz.cast(value)
    }
}
