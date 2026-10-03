package com.ephemeral

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

/**
 * An in-process key-value store whose entries expire after a caller-supplied duration.
 *
 * Reading a key whose value has expired, or was never stored, yields `null`. Reading a key
 * as a type other than the one stored under it throws [ClassCastException]: that is a
 * programming error rather than a runtime condition, so it is not folded into the `null` case.
 *
 * All operations are safe to call from any thread. Expired entries are dropped when they are
 * read, and swept out in bulk by [purgeExpired], which [put] runs at most once per second.
 */
object InMemory {

    /** Deliberately not a data class: entries are compared by identity in replace/remove. */
    private class Entry(val value: Any, val expiresAtMillis: Long) {
        fun hasExpired(): Boolean = common.hasExpired(expiresAtMillis)
    }

    private val store = ConcurrentHashMap<String, Entry>()
    private val lastPurgeAtMillis = AtomicLong(0L)

    /** Stores [value] under [key]. It can be read back until [expireAfter] has elapsed. */
    fun <T : Any> put(key: String, value: T, expireAfter: Duration) {
        store[key] = Entry(value, common.expiresAt(expireAfter))
        purgeIfDue()
    }

    /** Returns the value stored under [key], or `null` if there is none or it has expired. */
    fun <T : Any> get(key: String, clazz: KClass<T>): T? {
        val entry = store[key] ?: return null
        if (entry.hasExpired()) {
            store.remove(key, entry)
            return null
        }
        return common.cast(key, entry.value, clazz)
    }

    /** Returns the value stored under [key], or `null` if there is none or it has expired. */
    inline fun <reified T : Any> get(key: String): T? = get(key, T::class)

    /**
     * Returns the value stored under [key] and, if present and not expired, resets its expiry
     * so that it expires [expireAfter] from now. An expired value is removed and `null` returned;
     * it is never brought back to life.
     */
    fun <T : Any> getAndUpdateExpiryIfPresent(key: String, expireAfter: Duration, clazz: KClass<T>): T? {
        val entry = store.computeIfPresent(key) { _, current ->
            if (current.hasExpired()) null else Entry(current.value, common.expiresAt(expireAfter))
        } ?: return null
        return common.cast(key, entry.value, clazz)
    }

    /**
     * Returns the value stored under [key] and, if present and not expired, resets its expiry
     * so that it expires [expireAfter] from now. An expired value is removed and `null` returned;
     * it is never brought back to life.
     */
    inline fun <reified T : Any> getAndUpdateExpiryIfPresent(key: String, expireAfter: Duration): T? =
        getAndUpdateExpiryIfPresent(key, expireAfter, T::class)

    /**
     * Replaces the value stored under [key] with the result of [updateFunc], keeping its
     * current expiry. Returns `true` if a live value was present and updated, `false` if the
     * key is absent or its value has expired.
     *
     * [updateFunc] runs without holding any lock, so it may freely call back into this store.
     * If another thread writes the key concurrently, [updateFunc] is re-run on the new value.
     */
    fun <T : Any> updateValueIfPresent(key: String, clazz: KClass<T>, updateFunc: (T) -> T): Boolean {
        while (true) {
            val current = store[key] ?: return false
            if (current.hasExpired()) {
                store.remove(key, current)
                return false
            }
            val updated = Entry(updateFunc(common.cast(key, current.value, clazz)), current.expiresAtMillis)
            if (store.replace(key, current, updated)) return true
        }
    }

    /**
     * Replaces the value stored under [key] with the result of [updateFunc], keeping its
     * current expiry. Returns `true` if a live value was present and updated, `false` if the
     * key is absent or its value has expired.
     */
    inline fun <reified T : Any> updateValueIfPresent(key: String, noinline updateFunc: (T) -> T): Boolean =
        updateValueIfPresent(key, T::class, updateFunc)

    /**
     * Returns the live value stored under [key]. If there is none, runs [compute], stores its
     * result under [key] with an expiry of [expireAfter] from now, and returns it.
     *
     * [compute] runs without holding any lock, so it may block (for example on a network call)
     * and may call back into this store. If several threads miss the same key at once, each
     * runs [compute]; the first result to be stored is kept and returned to all of them.
     */
    fun <T : Any> getOrPut(key: String, expireAfter: Duration, clazz: KClass<T>, compute: () -> T): T {
        get(key, clazz)?.let { return it }
        val computed = compute()
        val entry = Entry(computed, common.expiresAt(expireAfter))
        while (true) {
            val existing = store.putIfAbsent(key, entry)
            if (existing == null) {
                purgeIfDue()
                return computed
            }
            if (!existing.hasExpired()) {
                return common.cast(key, existing.value, clazz)
            }
            if (store.replace(key, existing, entry)) {
                purgeIfDue()
                return computed
            }
        }
    }

    /**
     * Returns the live value stored under [key]. If there is none, runs [compute], stores its
     * result under [key] with an expiry of [expireAfter] from now, and returns it.
     */
    inline fun <reified T : Any> getOrPut(key: String, expireAfter: Duration, noinline compute: () -> T): T =
        getOrPut(key, expireAfter, T::class, compute)

    /** Removes [key]. Returns `true` if a live (unexpired) value was stored under it. */
    fun remove(key: String): Boolean {
        val removed = store.remove(key) ?: return false
        return !removed.hasExpired()
    }

    /**
     * Drops every expired entry. [put] calls this automatically at most once per second, so
     * a store that is only ever written to does not grow without bound. Call it directly to
     * reclaim memory sooner.
     */
    fun purgeExpired() {
        val now = common.nowMillis()
        store.values.removeIf { now >= it.expiresAtMillis }
    }

    private fun purgeIfDue() {
        val now = common.nowMillis()
        val last = lastPurgeAtMillis.get()
        if (common.purgeDue(last, now) && lastPurgeAtMillis.compareAndSet(last, now)) {
            purgeExpired()
        }
    }

    /** Number of entries held, expired or not. For tests. */
    internal fun entryCount(): Int = store.size

    /** Drops every entry and resets the purge timer. For tests. */
    internal fun reset() {
        store.clear()
        lastPurgeAtMillis.set(0L)
    }
}
