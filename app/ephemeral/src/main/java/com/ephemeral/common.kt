package com.ephemeral

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.lang.reflect.Type
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
}

data class CastError(val msg: String)