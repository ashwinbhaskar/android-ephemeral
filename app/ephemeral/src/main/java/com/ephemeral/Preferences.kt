package com.ephemeral

import android.content.Context
import android.content.SharedPreferences
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicLong

/**
 * Shared-preferences storage for primitives with an expiry.
 *
 * Values live in a private preferences file. The instant at which each key expires lives in a
 * second private file under the same key, so the bookkeeping can never collide with a caller's
 * own keys. A value whose expiry has passed, or which has no recorded expiry, is treated as
 * absent: it is removed on read and the caller's default is returned.
 *
 * Expired entries are also swept out in bulk by [purgeExpired], which every put runs at most
 * once per second, so a store that is only ever written to does not grow without bound.
 */
object Preferences {

    private const val VALUES_FILE = "ephemeral_persisted_preferences"
    private const val EXPIRY_FILE = "ephemeral_persisted_preferences_expiry"

    private val lastPurgeAtMillis = AtomicLong(0L)

    private fun values(context: Context): SharedPreferences =
        context.getSharedPreferences(VALUES_FILE, Context.MODE_PRIVATE)

    private fun expiries(context: Context): SharedPreferences =
        context.getSharedPreferences(EXPIRY_FILE, Context.MODE_PRIVATE)

    private fun put(
        key: String,
        expireAfter: Duration,
        context: Context,
        write: (SharedPreferences.Editor) -> SharedPreferences.Editor
    ) {
        val values = values(context)
        // Record the expiry first: a value without one reads as absent, so a crash between the
        // two writes can lose this write but can never leave a value that outlives its expiry.
        expiries(context).edit().putLong(key, common.expiresAt(expireAfter)).apply()
        Legacy.remove(write(values.edit()), values, key).apply()
        purgeIfDue(context)
    }

    private fun <T> get(key: String, default: T, context: Context, read: (SharedPreferences) -> T): T {
        val values = values(context)
        val expiresAt = expiresAt(key, context)
        if (expiresAt != null && !common.hasExpired(expiresAt)) {
            return read(values)
        }
        if (expiresAt != null || values.contains(key)) {
            removeKey(key, context)
        }
        return default
    }

    /** The recorded expiry of [key] in epoch milliseconds, or `null` when none is recorded. */
    private fun expiresAt(key: String, context: Context): Long? {
        val expiries = expiries(context)
        if (expiries.contains(key)) {
            return expiries.getLong(key, 0L)
        }
        val values = values(context)
        val legacy = Legacy.expiresAt(values, key) ?: return null
        // Move an expiry written by 1.x into the expiry file so later reads take the fast path.
        expiries.edit().putLong(key, legacy).apply()
        Legacy.remove(values.edit(), values, key).apply()
        return legacy
    }

    /** Removes [key] and its expiry. Does nothing if the key is absent. */
    fun removeKey(key: String, context: Context) {
        val values = values(context)
        Legacy.remove(values.edit().remove(key), values, key).apply()
        expiries(context).edit().remove(key).apply()
    }

    /**
     * Drops every expired entry. Every put calls this automatically at most once per second;
     * call it directly to reclaim space sooner.
     */
    fun purgeExpired(context: Context) {
        val now = common.nowMillis()
        val values = values(context)
        val expiries = expiries(context)
        val valuesEditor = values.edit()
        val expiriesEditor = expiries.edit()
        var removedAny = false

        for ((key, expiresAt) in expiries.all) {
            if (expiresAt is Long && now >= expiresAt) {
                valuesEditor.remove(key)
                expiriesEditor.remove(key)
                removedAny = true
            }
        }
        for ((key, raw) in values.all) {
            val expiresAt = Legacy.parse(key, raw) ?: continue
            if (now >= expiresAt) {
                valuesEditor.remove(key).remove(Legacy.valueKey(key))
                removedAny = true
            }
        }

        if (removedAny) {
            valuesEditor.apply()
            expiriesEditor.apply()
        }
    }

    private fun purgeIfDue(context: Context) {
        val now = common.nowMillis()
        val last = lastPurgeAtMillis.get()
        if (common.purgeDue(last, now) && lastPurgeAtMillis.compareAndSet(last, now)) {
            purgeExpired(context)
        }
    }

    /** Resets the purge timer. For tests. */
    internal fun reset() {
        lastPurgeAtMillis.set(0L)
    }

    /**
     * Version 1.x kept each expiry in the values file, under the value's key plus `_expiry`,
     * as an ISO local date-time string. Those entries are still honoured, and migrated or
     * removed as they are encountered. A caller's own key that happens to end in `_expiry`
     * is left alone unless its value parses as such a date-time.
     */
    private object Legacy {
        private const val SUFFIX = "_expiry"
        private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        fun expiryKey(key: String): String = key + SUFFIX

        fun valueKey(expiryKey: String): String = expiryKey.removeSuffix(SUFFIX)

        /** The 1.x expiry of [key] in epoch milliseconds, or `null` when there is none. */
        fun expiresAt(values: SharedPreferences, key: String): Long? {
            val expiryKey = expiryKey(key)
            val raw = try {
                values.getString(expiryKey, null)
            } catch (e: ClassCastException) {
                null
            }
            return parse(expiryKey, raw)
        }

        /** Interprets a values-file entry as a 1.x expiry, or `null` if it is not one. */
        fun parse(key: String, raw: Any?): Long? {
            if (!key.endsWith(SUFFIX) || raw !is String) return null
            return try {
                LocalDateTime.parse(raw, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (e: DateTimeParseException) {
                null
            }
        }

        /** Queues removal of the 1.x expiry for [key], if there is one, onto [editor]. */
        fun remove(editor: SharedPreferences.Editor, values: SharedPreferences, key: String): SharedPreferences.Editor {
            if (expiresAt(values, key) != null) {
                editor.remove(expiryKey(key))
            }
            return editor
        }
    }

    fun putBoolean(key: String, value: Boolean, expireAfter: Duration, context: Context) {
        put(key, expireAfter, context) { it.putBoolean(key, value) }
    }

    fun getBoolean(key: String, default: Boolean, context: Context): Boolean {
        return get(key, default, context) { it.getBoolean(key, default) }
    }

    fun putString(key: String, value: String, expireAfter: Duration, context: Context) {
        put(key, expireAfter, context) { it.putString(key, value) }
    }

    fun getString(key: String, default: String, context: Context): String {
        return get(key, default, context) { it.getString(key, default) ?: default }
    }

    fun putFloat(key: String, value: Float, expireAfter: Duration, context: Context) {
        put(key, expireAfter, context) { it.putFloat(key, value) }
    }

    fun getFloat(key: String, default: Float, context: Context): Float {
        return get(key, default, context) { it.getFloat(key, default) }
    }

    fun putInt(key: String, value: Int, expireAfter: Duration, context: Context) {
        put(key, expireAfter, context) { it.putInt(key, value) }
    }

    fun getInt(key: String, default: Int, context: Context): Int {
        return get(key, default, context) { it.getInt(key, default) }
    }

    fun putLong(key: String, value: Long, expireAfter: Duration, context: Context) {
        put(key, expireAfter, context) { it.putLong(key, value) }
    }

    fun getLong(key: String, default: Long, context: Context): Long {
        return get(key, default, context) { it.getLong(key, default) }
    }

    fun putStringSet(key: String, value: Set<String>, expireAfter: Duration, context: Context) {
        put(key, expireAfter, context) { it.putStringSet(key, value) }
    }

    fun getStringSet(key: String, default: Set<String>, context: Context): Set<String> {
        return get(key, default, context) { it.getStringSet(key, default) ?: default }
    }
}
