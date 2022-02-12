package com.ephemeral

import arrow.core.*
import arrow.core.Either.*
import java.lang.Exception
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.cast

object inmemory {

    private val store: MutableMap<String, common.Value<*>> = mutableMapOf()

    fun <T>put(key: String, value: T, expireAfter: Duration) {
        val now = LocalDateTime.now()
        val expiryDateTime = now.plusNanos(expireAfter.toNanos())
        store[key] = common.Value(value, expiryDateTime)
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
                expiry = value.expiry.plusNanos(
                    expireAfter.toNanos()
                )
            )
        }.toOption()) {
            is None -> None.right()
            is Some -> common.tryCast(result.value, clazz)
        }
    }

    fun <T>updateValueIfPresent(key: String, newValue: T): Boolean {
        return when(val result = store[key].toOption()) {
            is None -> false
            is Some -> {
                store[key] = common.Value(newValue, result.value.expiry)
                true
            }
        }
    }

    fun remove(key: String): Boolean {
        return store.remove(key) != null
    }
}