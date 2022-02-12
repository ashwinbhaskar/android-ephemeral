package com.ephemeral

import android.content.Context
import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.right
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

object Persisted {

    private fun prefs(context: Context) =
        context.getSharedPreferences("ephemeral_persisted_preferences", Context.MODE_PRIVATE)

    fun removeKey(key: String, context: Context) {
        prefs(context).edit().remove(key).apply()
    }

    private fun <T> put(key: String, value: T, expiry: LocalDateTime, context: Context) {
        val v = common.Value(value, expiry)
        val json = common.gson.toJson(v)
        prefs(context).edit().putString(key, json).apply()
    }

    fun <T> put(key: String, value: T, expireAfter: Duration, context: Context) {
        put(key, value, common.expiry(expireAfter), context)
    }

    fun <T : Any> get(
        key: String,
        clazz: KClass<T>,
        context: Context
    ): Either<CastError, Option<T>> {
        return when (val s = prefs(context).getString(key, "")) {
            "", null -> None.right()
            else -> {
                val value = common.gson.fromJson(s, common.Value::class.java)
                if (common.hasExpired(value.expiry)) {
                    removeKey(key, context)
                    None.right()
                } else {
                    common.tryCast(value, clazz)
                }
            }
        }
    }


    fun <T : Any> getAndUpdateExpiryIfPresent(
        key: String,
        newExpireAfter: Duration,
        clazz: KClass<T>,
        context: Context
    ): Either<CastError, Option<T>> {
        return get(key, clazz, context).map { maybeValue ->
            maybeValue.map { value ->
                put(key, value, newExpireAfter, context)
                value
            }
        }
    }


    @Synchronized
    fun <T : Any> updateValueIfPresent(
        key: String,
        updateFunc: (T) -> T,
        clazz: KClass<T>,
        context: Context
    ): Either<CastError, Boolean> {
        return when (val s = prefs(context).getString(key, "")) {
            "", null -> false.right()
            else -> {
                val value = common.gson.fromJson(s, common.Value::class.java)
                if (common.hasExpired(value.expiry)) {
                    removeKey(key, context)
                    false.right()
                } else {
                    val expiry = value.expiry
                    common
                        .tryCast(value, clazz)
                        .map { maybeValue ->
                            maybeValue.fold({ false }, { value ->
                                val newValue = updateFunc(value)
                                put(key, newValue, expiry, context)
                                true
                            })
                        }
                }
            }
        }
    }
}
