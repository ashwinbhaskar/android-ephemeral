package com.ephemeral.Extensions

import android.content.Context
import com.ephemeral.Preferences
import java.time.Duration

fun Context.putEphemeralInt(key: String, value: Int, expireAfter: Duration) =
    Preferences.putInt(key, value, expireAfter, this)

fun Context.getEphemeralInt(key: String, default: Int): Int =
    Preferences.getInt(key, default, this)

fun Context.putEphemeralString(key: String, value: String, expireAfter: Duration) =
    Preferences.putString(key, value, expireAfter, this)

fun Context.getEphemeralString(key: String, default: String): String =
    Preferences.getString(key, default, this)

fun Context.putEphemeralBoolean(key: String, value: Boolean, expireAfter: Duration) =
    Preferences.putBoolean(key, value, expireAfter, this)

fun Context.getEphemeralBoolean(key: String, default: Boolean): Boolean =
    Preferences.getBoolean(key, default, this)

fun Context.putEphemeraFloat(key: String, value: Float, expireAfter: Duration) =
    Preferences.putFloat(key, value, expireAfter, this)

fun Context.getEphemeralFloat(key: String, default: Float): Float =
    Preferences.getFloat(key, default, this)

fun Context.putEphemeralLong(key: String, value: Long, expireAfter: Duration) =
    Preferences.putLong(key, value, expireAfter, this)

fun Context.getEphemeralLong(key: String, default: Long): Long =
    Preferences.getLong(key, default, this)

fun Context.getEphemeralStringSet(key: String, default: Set<String>): Set<String> =
    Preferences.getStringSet(key, default, this)

fun Context.putEphemeralStringSet(key: String, value: Set<String>, expireAfter: Duration) =
    Preferences.putStringSet(key, value, expireAfter, this)