package com.ephemeral.Extensions

import android.content.Context
import arrow.core.Either
import arrow.core.Option
import com.ephemeral.CastError
import com.ephemeral.Persisted
import java.time.Duration
import kotlin.reflect.KClass

fun Context.removeKey(key: String) {
    Persisted.removeKey(key, this)
}

fun <T : Any> Context.put(
    key: String,
    value: T,
    expireAfter: Duration,
    clazz: KClass<T>
) {
    Persisted.put(key, value, expireAfter, clazz, this)
}

fun <T : Any> Context.getAndUpdateExpiryIfPresent(
    key: String,
    newExpireAfter: Duration,
    clazz: KClass<T>
): Either<CastError, Option<T>> {
    return Persisted.getAndUpdateExpiryIfPresent(key, newExpireAfter, clazz, this)
}

fun <T : Any> Context.updateValueIfPresent(
    key: String,
    updateFunc: (T) -> T,
    clazz: KClass<T>
): Either<CastError, Boolean> {
    return Persisted.updateValueIfPresent(key, updateFunc, clazz, this)
}

fun <T : Any> Context.get(
    key: String,
    clazz: KClass<T>
): Either<CastError, Option<T>> {
    return Persisted.get(key, clazz, this)
}
