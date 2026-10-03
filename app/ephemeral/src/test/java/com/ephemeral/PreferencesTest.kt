package com.ephemeral

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PreferencesTest {

    private companion object {
        const val VALUES_FILE = "ephemeral_persisted_preferences"
        const val EXPIRY_FILE = "ephemeral_persisted_preferences_expiry"
    }

    private var now = 1_700_000_000_000L
    private val files = HashMap<String, FakeSharedPreferences>()
    private val context: Context = Mockito.mock(Context::class.java).also { ctx ->
        Mockito.`when`(ctx.getSharedPreferences(anyString(), anyInt()))
            .thenAnswer { invocation -> files.getOrPut(invocation.getArgument(0)) { FakeSharedPreferences() } }
    }

    private val values get() = files.getOrPut(VALUES_FILE) { FakeSharedPreferences() }
    private val expiries get() = files.getOrPut(EXPIRY_FILE) { FakeSharedPreferences() }

    private fun advance(by: Duration) {
        now += by.toMillis()
    }

    @Before
    fun setUp() {
        common.nowMillis = { now }
        Preferences.reset()
    }

    @After
    fun tearDown() {
        common.nowMillis = { System.currentTimeMillis() }
        Preferences.reset()
    }

    @Test
    fun `stores and reads back every supported type`() {
        val ttl = Duration.ofMinutes(5)
        Preferences.putString("s", "value", ttl, context)
        Preferences.putBoolean("b", true, ttl, context)
        Preferences.putInt("i", 7, ttl, context)
        Preferences.putLong("l", 8L, ttl, context)
        Preferences.putFloat("f", 1.5f, ttl, context)
        Preferences.putStringSet("set", setOf("x", "y"), ttl, context)

        assertEquals("value", Preferences.getString("s", "", context))
        assertEquals(true, Preferences.getBoolean("b", false, context))
        assertEquals(7, Preferences.getInt("i", 0, context))
        assertEquals(8L, Preferences.getLong("l", 0L, context))
        assertEquals(1.5f, Preferences.getFloat("f", 0f, context))
        assertEquals(setOf("x", "y"), Preferences.getStringSet("set", emptySet(), context))
    }

    @Test
    fun `returns the default for a key that was never stored without writing anything`() {
        assertEquals("default", Preferences.getString("missing", "default", context))
        assertTrue(values.all.isEmpty())
        assertTrue(expiries.all.isEmpty())
    }

    @Test
    fun `returns the default once the value has expired and removes both entries`() {
        Preferences.putString("s", "value", Duration.ofSeconds(2), context)

        advance(Duration.ofMillis(1999))
        assertEquals("value", Preferences.getString("s", "default", context))

        advance(Duration.ofMillis(1))
        assertEquals("default", Preferences.getString("s", "default", context))
        assertFalse(values.contains("s"))
        assertFalse(expiries.contains("s"))
    }

    @Test
    fun `expiry is stored as epoch milliseconds, not a local date-time string`() {
        Preferences.putString("s", "value", Duration.ofSeconds(30), context)

        assertEquals(now + 30_000L, expiries.getLong("s", 0L))
    }

    @Test
    fun `a value with no recorded expiry is treated as absent and removed`() {
        values.edit().putString("orphan", "value").apply()

        assertEquals("default", Preferences.getString("orphan", "default", context))
        assertFalse(values.contains("orphan"))
    }

    @Test
    fun `removeKey removes the value and its expiry`() {
        Preferences.putInt("i", 1, Duration.ofMinutes(1), context)

        Preferences.removeKey("i", context)

        assertFalse(values.contains("i"))
        assertFalse(expiries.contains("i"))
        assertEquals(0, Preferences.getInt("i", 0, context))
    }

    @Test
    fun `overwriting a key resets its expiry`() {
        Preferences.putInt("i", 1, Duration.ofSeconds(1), context)
        advance(Duration.ofMillis(900))
        Preferences.putInt("i", 2, Duration.ofSeconds(1), context)

        advance(Duration.ofMillis(900))
        assertEquals(2, Preferences.getInt("i", 0, context))
    }

    @Test
    fun `a caller's own key ending in _expiry is never touched`() {
        Preferences.putString("foo_expiry", "mine", Duration.ofMinutes(5), context)
        Preferences.putString("foo", "value", Duration.ofMinutes(5), context)

        assertEquals("value", Preferences.getString("foo", "", context))
        assertEquals("mine", Preferences.getString("foo_expiry", "", context))

        Preferences.removeKey("foo", context)
        assertEquals("mine", Preferences.getString("foo_expiry", "", context))

        advance(Duration.ofMinutes(10))
        Preferences.putInt("other", 1, Duration.ofMinutes(5), context)
        assertFalse(values.contains("foo_expiry"))
        assertFalse(expiries.contains("foo_expiry"))
    }

    @Test
    fun `honours and migrates an unexpired expiry written by version 1`() {
        values.edit()
            .putString("legacy", "value")
            .putString("legacy_expiry", legacyExpiryString(now + 60_000L))
            .apply()

        assertEquals("value", Preferences.getString("legacy", "default", context))
        assertFalse(values.contains("legacy_expiry"))
        assertEquals(now + 60_000L, expiries.getLong("legacy", 0L))

        advance(Duration.ofSeconds(61))
        assertEquals("default", Preferences.getString("legacy", "default", context))
        assertFalse(values.contains("legacy"))
    }

    @Test
    fun `removes a value whose version 1 expiry has passed`() {
        values.edit()
            .putString("legacy", "value")
            .putString("legacy_expiry", legacyExpiryString(now - 1L))
            .apply()

        assertEquals("default", Preferences.getString("legacy", "default", context))
        assertFalse(values.contains("legacy"))
        assertFalse(values.contains("legacy_expiry"))
        assertFalse(expiries.contains("legacy"))
    }

    @Test
    fun `overwriting a version 1 key drops its old expiry entry`() {
        values.edit()
            .putString("legacy", "old")
            .putString("legacy_expiry", legacyExpiryString(now + 60_000L))
            .apply()

        Preferences.putString("legacy", "new", Duration.ofMinutes(1), context)

        assertFalse(values.contains("legacy_expiry"))
        assertEquals("new", Preferences.getString("legacy", "", context))
    }

    @Test
    fun `put sweeps out expired entries that were never read`() {
        Preferences.putInt("a", 1, Duration.ofSeconds(1), context)
        Preferences.putInt("b", 2, Duration.ofSeconds(1), context)
        values.edit()
            .putString("legacy", "value")
            .putString("legacy_expiry", legacyExpiryString(now + 1_000L))
            .apply()

        advance(Duration.ofSeconds(2))
        Preferences.putInt("c", 3, Duration.ofMinutes(1), context)

        assertEquals(setOf("c"), values.all.keys)
        assertEquals(setOf("c"), expiries.all.keys)
    }

    @Test
    fun `put does not sweep more than once per second`() {
        Preferences.putInt("a", 1, Duration.ofMillis(500), context)
        advance(Duration.ofMillis(999))
        // "a" has expired, but the sweep triggered by the first put was under a second ago.
        Preferences.putInt("b", 2, Duration.ofMinutes(1), context)
        assertTrue(values.contains("a"))

        advance(Duration.ofMillis(1))
        Preferences.putInt("c", 3, Duration.ofMinutes(1), context)
        assertFalse(values.contains("a"))
    }

    @Test
    fun `purgeExpired drops every expired entry`() {
        Preferences.putInt("a", 1, Duration.ofSeconds(1), context)
        Preferences.putInt("b", 2, Duration.ofMinutes(1), context)
        advance(Duration.ofSeconds(1))

        Preferences.purgeExpired(context)

        assertEquals(setOf("b"), values.all.keys)
        assertEquals(setOf("b"), expiries.all.keys)
    }

    private fun legacyExpiryString(epochMillis: Long): String =
        LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}
