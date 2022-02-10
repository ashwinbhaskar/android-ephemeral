package com.ephemeral

import arrow.core.*
import arrow.core.Either.*
import java.lang.Exception
import java.time.Duration
import java.time.LocalDateTime
import kotlin.reflect.KClass

object inmemory {

    private val store: MutableMap<String, common.Value<*>> = mutableMapOf()

    fun <T>put(key: String, value: T, expireAfter: Duration) {
        val now = LocalDateTime.now()
        val expiryDateTime = now.plusNanos(expireAfter.toNanos())
        store[key] = common.Value(value, expiryDateTime)
    }

    fun <T>get(key: String, clazz: Class<T>): Either<CastError, Option<T>> {
        return when (val result = store[key].toOption()) {
            is None -> None.right()
            is Some -> if (common.hasExpired(result.value.expiry)) {
                store.remove(key)
                None.right()
            }
            else try{
                clazz.cast(result.value.v)!!.some().right()
            } catch (e: Exception){
                CastError(e.message ?: "").left()
            }
        }
    }

    fun <T>getUnsafe(key: String, clazz: Class<T>): Option<T> {
        return when (val result = store[key].toOption()) {
            is None -> None
            is Some -> if (common.hasExpired(result.value.expiry)) {
                store.remove(key)
                None
            }
            else clazz.cast(result.value.v)!!.some()
        }
    }
}