package com.ephemeral

import arrow.core.*
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.ClassCastException
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal object common {

    data class Value<out T>(val v: T, val expiry: LocalDateTime)

    private data class ValueWithClassStamp<out T>(val value: Value<T>, val clazz: String)

    fun now(): LocalDateTime = LocalDateTime.now()

    private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private val localDateTimeAdapter = object : TypeAdapter<LocalDateTime>() {
        override fun write(out: JsonWriter?, value: LocalDateTime?) {
            out?.value(
                value?.let {
                    dateTimeFormatter.format(it)
                } ?: dateTimeFormatter.format(now())
            )
        }

        override fun read(`in`: JsonReader?): LocalDateTime {
            return `in`?.nextString()?.let {
                LocalDateTime.parse(it, dateTimeFormatter)
            } ?: now()
        }
    }

    private val gson: Gson =
        GsonBuilder().registerTypeAdapter(LocalDateTime::class.java, localDateTimeAdapter).create()

    fun expiry(duration: Duration): LocalDateTime =
        now().plusNanos(duration.toNanos())

    fun hasExpired(expiry: LocalDateTime): Boolean =
        now().isAfter(expiry)

    fun <T: Any>tryCast(value: common.Value<*>, clazz: KClass<T>): Either<CastError, Option<T>> =
        try {
            clazz.cast(value.v).some().right()
        } catch (e: ClassCastException) {
            CastError(e.message ?: "").left()
        }

    private val rawType = TypeToken.get(Value::class.java).type

    private val rawValueWithClassStampType = TypeToken.get(ValueWithClassStamp::class.java).type

    private fun <T: Any>getType(clazz: KClass<T>): Type {
        val paramType = TypeToken.get(clazz.java).type
        return TypeToken.getParameterized(rawValueWithClassStampType, paramType).type
    }

    fun <T: Any>serialize(value: Value<T>, clazz: KClass<T>): String {
        val type = getType(clazz)
        val valueWithClassStamp = ValueWithClassStamp(value, clazz.qualifiedName!!)
        return gson.toJson(valueWithClassStamp, type)
    }

    fun <T: Any>deserialize(s: String, clazz: KClass<T>): Either<CastError, Value<T>> {
        val type = getType(clazz)
        val valueWithClassStamp: ValueWithClassStamp<T> = gson.fromJson(s, type)
        return if(valueWithClassStamp.clazz == clazz.qualifiedName) {
            valueWithClassStamp.value.right()
        } else {
            CastError("cannot cast ${valueWithClassStamp.clazz} to ${clazz.qualifiedName}").left()
        }
    }
}

data class CastError(val msg: String)

fun <T>Either<CastError, T>.unsafe(): T {
    return when(this) {
        is Either.Left -> throw ClassCastException(this.value.msg)
        is Either.Right -> this.value
    }
}
