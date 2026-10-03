package com.ephemeral

import java.time.Duration
import kotlin.reflect.KClass

/**
 * An in-process key-value store whose entries expire after a caller-supplied duration.
 *
 * Reading a key whose value has expired, or was never stored, yields `null`. Reading a key
 * as a type other than the one stored under it throws [ClassCastException]: that is a
 * programming error rather than a runtime condition, so it is not folded into the `null` case.
 */
object InMemory {

    private val store: MutableMap<String, common.Value<*>> = mutableMapOf()

    /** Stores [value] under [key]. It can be read back until [expireAfter] has elapsed. */
    fun <T : Any> put(key: String, value: T, expireAfter: Duration) {
        store[key] = common.Value(value, common.expiry(expireAfter))
    }

    /** Returns the value stored under [key], or `null` if there is none or it has expired. */
    fun <T : Any> get(key: String, clazz: KClass<T>): T? {
        val entry = store[key] ?: return null
        if (common.hasExpired(entry.expiry)) {
            store.remove(key)
            return null
        }
        return common.cast(key, entry.v, clazz)
    }

    /** Returns the value stored under [key], or `null` if there is none or it has expired. */
    inline fun <reified T : Any> get(key: String): T? = get(key, T::class)

    /**
     * Returns the value stored under [key] and, if present, resets its expiry so that it
     * expires [expireAfter] from now.
     */
    fun <T : Any> getAndUpdateExpiryIfPresent(key: String, expireAfter: Duration, clazz: KClass<T>): T? {
        val entry = store.computeIfPresent(key) { _, value ->
            value.copy(expiry = common.expiry(expireAfter))
        } ?: return null
        return common.cast(key, entry.v, clazz)
    }

    /**
     * Returns the value stored under [key] and, if present, resets its expiry so that it
     * expires [expireAfter] from now.
     */
    inline fun <reified T : Any> getAndUpdateExpiryIfPresent(key: String, expireAfter: Duration): T? =
        getAndUpdateExpiryIfPresent(key, expireAfter, T::class)

    /**
     * Replaces the value stored under [key] with the result of [updateFunc], keeping its
     * current expiry. Returns `true` if a value was present and updated.
     */
    @Synchronized
    fun <T : Any> updateValueIfPresent(key: String, clazz: KClass<T>, updateFunc: (T) -> T): Boolean {
        val entry = store[key] ?: return false
        val current = common.cast(key, entry.v, clazz)
        store[key] = common.Value(updateFunc(current), entry.expiry)
        return true
    }

    /**
     * Replaces the value stored under [key] with the result of [updateFunc], keeping its
     * current expiry. Returns `true` if a value was present and updated.
     */
    inline fun <reified T : Any> updateValueIfPresent(key: String, noinline updateFunc: (T) -> T): Boolean =
        updateValueIfPresent(key, T::class, updateFunc)

    /** Removes [key]. Returns `true` if a value was stored under it. */
    fun remove(key: String): Boolean {
        return store.remove(key) != null
    }
}
