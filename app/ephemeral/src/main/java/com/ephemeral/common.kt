package com.ephemeral

import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal object common {

    /** Source of the current time in epoch milliseconds. Tests replace it with a fake clock. */
    @Volatile
    var nowMillis: () -> Long = { System.currentTimeMillis() }

    /** Minimum gap between two opportunistic purges of expired entries triggered by writes. */
    const val PURGE_INTERVAL_MILLIS = 1_000L

    /** The instant, in epoch milliseconds, at which a value written now with [expireAfter] expires. */
    fun expiresAt(expireAfter: Duration): Long {
        val now = nowMillis()
        val millis = try {
            expireAfter.toMillis()
        } catch (e: ArithmeticException) {
            Long.MAX_VALUE
        }
        return if (millis > Long.MAX_VALUE - now) Long.MAX_VALUE else now + millis
    }

    fun hasExpired(expiresAtMillis: Long): Boolean =
        nowMillis() >= expiresAtMillis

    /**
     * Returns true when a purge is due, given the time of the last one. A clock that has moved
     * backwards also counts as due, so a purge is never postponed indefinitely.
     */
    fun purgeDue(lastPurgeAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis < lastPurgeAtMillis || nowMillis - lastPurgeAtMillis >= PURGE_INTERVAL_MILLIS

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
