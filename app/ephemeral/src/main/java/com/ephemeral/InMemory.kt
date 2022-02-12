package com.ephemeral

import arrow.core.*
import java.time.Duration
import kotlin.reflect.KClass

object InMemory {

    private val store: MutableMap<String, common.Value<*>> = mutableMapOf()

    fun <T>put(key: String, value: T, expireAfter: Duration) {
        store[key] = common.Value(value, common.expiry(expireAfter))
    }

    fun <T: Any>get(key: String, clazz: KClass<T>): Either<CastError, Option<T>> {
        return when (val result = store[key].toOption()) {
            is None -> None.right()
            is Some -> if (common.hasExpired(result.value.expiry)) {
                store.remove(key)
                None.right()
            }
            else common.tryCast(result.value, clazz)
        }
    }

    fun <T: Any>getAndUpdateExpiryIfPresent(key: String, expireAfter: Duration, clazz: KClass<T>): Either<CastError, Option<T>> {
        return when(val result = store.computeIfPresent(key) { _, value ->
            value.copy(
                expiry = common.expiry(expireAfter)
            )
        }.toOption()) {
            is None -> None.right()
            is Some -> common.tryCast(result.value, clazz)
        }
    }

    @Synchronized
    fun <T: Any>updateValueIfPresent(key: String, updateFunc: (T) -> T, clazz: KClass<T>): Either<CastError, Boolean> {
        return when(val result = store[key].toOption()) {
            is None -> false.right()
            is Some -> common.tryCast(result.value, clazz)
                .map { maybeValue ->
                    maybeValue.fold({false}, {value ->
                        store[key] = common.Value(updateFunc(value), result.value.expiry)
                        true
                    })
                }
        }
    }

    fun remove(key: String): Boolean {
        return store.remove(key) != null
    }
}