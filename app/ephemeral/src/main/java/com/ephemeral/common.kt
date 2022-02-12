package com.ephemeral

import arrow.core.*
import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.ClassCastException
import java.lang.Exception
import java.lang.reflect.Type
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.cast

internal object common {

    data class Value<T>(val v: T, val expiry: LocalDateTime)

    data class PersistedValue<T>(val v: T, val expiry: LocalDateTime)

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

    val gson: Gson =
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
}

data class CastError(val msg: String)

fun <T>Either<CastError, Option<T>>.unsafe(): Option<T> {
    return when(this) {
        is Either.Left -> throw ClassCastException(this.value.msg)
        is Either.Right -> this.value
    }
}
