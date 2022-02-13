package com.ephemeral

import android.content.Context
import android.content.SharedPreferences
import java.time.Duration
import java.time.LocalDateTime

object Preferences {

    private fun prefs(context: Context) =
        context.getSharedPreferences("ephemeral_persisted_preferences", Context.MODE_PRIVATE)

    private const val expiryTimeKeySuffix = "_expiry"

    private val defaultTimeStr = common.format(LocalDateTime.of(2020, 11, 30, 5, 5))

    private val expiryKey: (String) -> String = {it + expiryTimeKeySuffix}

    private fun put(key: String, fn: (SharedPreferences.Editor) -> SharedPreferences.Editor, expireAfter: Duration, context: Context) {
        fn(prefs(context).edit())
            .putString(expiryKey(key), common.expiryStr(expireAfter))
            .apply()
    }

    fun removeKey(key: String, context: Context) {
        prefs(context).edit().remove(key).apply()
    }


    private fun <T> get(key: String, fn: (SharedPreferences) -> T,  default: T, c: Context): T {
        val sharedPrefs = prefs(c)
        val expiryStr = sharedPrefs.getString(expiryKey(key), defaultTimeStr) ?: defaultTimeStr
        return if(common.hasExpired(expiryStr)){
            removeKey(key, c)
            removeKey(expiryKey(key), c)
            default
        } else {
            fn(sharedPrefs)
        }
    }

    fun putBoolean(key: String, value: Boolean, expireAfter: Duration, context: Context) {
        put(key, {editor -> editor.putBoolean(key, value) }, expireAfter, context)
    }

    fun getBoolean(key: String, default: Boolean,  context: Context): Boolean {
        return get(key, { it.getBoolean(key, default) }, default, context)
    }

    fun putString(key: String, value: String, expireAfter: Duration, context: Context) {
        put(key, {editor -> editor.putString(key, value) }, expireAfter, context)
    }

    fun getString(key: String, default: String, context: Context): String {
        return get(key, { prefs -> prefs.getString(key, default) ?: default }, default, context)
    }

    fun putFloat(key: String, value: Float, expireAfter: Duration, context: Context) {
        put(key, {editor -> editor.putFloat(key, value) }, expireAfter, context)
    }

    fun getFloat(key: String, default: Float, context: Context): Float {
        return get(key, { it.getFloat(key, default) }, default, context)
    }

    fun putInt(key: String, value: Int, expireAfter: Duration, context: Context) {
        put(key, {editor -> editor.putInt(key, value) }, expireAfter, context)
    }

    fun getInt(key: String, default: Int, context: Context): Int {
        return get(key, { it.getInt(key, default) }, default, context)
    }

    fun putLong(key: String, value: Long, expireAfter: Duration, context: Context) {
        put(key, {editor -> editor.putLong(key, value) }, expireAfter, context)
    }

    fun getLong(key: String, default: Long, context: Context): Long {
        return get(key, { it.getLong(key, default) }, default, context)
    }

    fun putStringSet(key: String, value: Set<String>, expireAfter: Duration, context: Context) {
        put(key, {editor -> editor.putStringSet(key, value) }, expireAfter, context)
    }

    fun getStringSet(key: String, default: Set<String>, context: Context): Set<String> {
        return get(key, { it.getStringSet(key, default) ?: default }, default, context)
    }
}
