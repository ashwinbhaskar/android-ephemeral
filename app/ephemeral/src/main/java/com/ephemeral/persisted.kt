package com.ephemeral

import android.content.Context
import arrow.core.*
import java.time.Duration
import kotlin.reflect.KClass
import kotlin.reflect.cast

object persisted {

    private fun prefs(context: Context) =
        context.getSharedPreferences("ephemeral_persisted_preferences", Context.MODE_PRIVATE)

    private fun removeKey(key: String, context: Context) {
        prefs(context).edit().remove(key).apply()
    }

    fun <T>put(key: String, value: T, expireAfter: Duration, context: Context) {
        val v = common.PersistedValue(value, common.expiry(expireAfter))
        val json = common.gson.toJson(v)
        prefs(context).edit().putString(key, json).apply()
    }

    fun <T: Any>get(key: String, clazz: KClass<T>, context: Context): Either<CastError, Option<T>> {
        return when (val s = prefs(context).getString(key, "")) {
            "", null -> None.right()
            else -> {
                try {
                    val value = common.gson.fromJson(s, common.Value::class.java)
                    if (common.hasExpired(value.expiry)) {
                        removeKey(key, context)
                        None.right()
                    } else {
                        clazz.cast(value.v).some().right()
                    }
                } catch (e: Exception) {
                    CastError(e.message?:"").left()
                }
            }
        }
    }
}