package com.ephemeral

import android.content.Context
import arrow.core.*
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

object Persisted {

    private fun prefs(context: Context) =
        context.getSharedPreferences("ephemeral_persisted_preferences", Context.MODE_PRIVATE)

    fun removeKey(key: String, context: Context) {
        prefs(context).edit().remove(key).apply()
    }

    private fun <T : Any> put(
        key: String,
        value: T,
        expiry: LocalDateTime,
        clazz: KClass<T>,
        context: Context
    ) {
        val v = common.Value(value, expiry)
        val json = common.serialize(v, clazz)
        prefs(context)
            .edit()
            .putString(key, json)
            .apply()
    }

    fun <T : Any> put(
        key: String,
        value: T,
        expireAfter: Duration,
        clazz: KClass<T>,
        context: Context
    ) {
        put(key, value, common.expiry(expireAfter), clazz, context)
    }

    fun <T : Any> get(
        key: String,
        clazz: KClass<T>,
        context: Context
    ): Either<CastError, Option<T>> {
        return when (val jsonStr = prefs(context).getString(key, "")) {
            "", null -> None.right()
            else -> {
                common
                    .deserialize(jsonStr, clazz)
                    .map { value ->
                        if (common.hasExpired(value.expiry)) {
                            removeKey(key, context)
                            None
                        } else {
                            value.v.some()
                        }
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
                put(key, value, newExpireAfter, clazz, context)
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
                common
                    .deserialize(s, clazz)
                    .map { value ->
                        if (common.hasExpired(value.expiry)) {
                            removeKey(key, context)
                            false
                        } else {
                            val newV = updateFunc(value.v)
                            put(key, newV, value.expiry, clazz, context)
                            true
                        }
                    }

            }
        }
    }

}
