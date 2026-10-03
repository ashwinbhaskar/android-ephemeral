package com.ephemeral

import android.content.SharedPreferences

/**
 * An in-memory SharedPreferences with the same observable semantics as the platform one:
 * typed getters throw ClassCastException on a type mismatch, and an editor's changes are
 * invisible until commit() or apply(), with clear() applied before any puts.
 */
class FakeSharedPreferences : SharedPreferences {

    private val map = HashMap<String, Any?>()

    override fun getAll(): MutableMap<String, *> = HashMap(map)

    override fun getString(key: String?, defValue: String?): String? =
        if (map.containsKey(key)) map[key] as String? else defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        if (map.containsKey(key)) (map[key] as Set<String>?)?.toMutableSet() else defValues

    override fun getInt(key: String?, defValue: Int): Int =
        if (map.containsKey(key)) map[key] as Int else defValue

    override fun getLong(key: String?, defValue: Long): Long =
        if (map.containsKey(key)) map[key] as Long else defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        if (map.containsKey(key)) map[key] as Float else defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        if (map.containsKey(key)) map[key] as Boolean else defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Editor : SharedPreferences.Editor {
        private val puts = LinkedHashMap<String, Any?>()
        private val removes = LinkedHashSet<String>()
        private var clear = false

        override fun putString(key: String?, value: String?) = set(key!!, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = set(key!!, values?.toSet())
        override fun putInt(key: String?, value: Int) = set(key!!, value)
        override fun putLong(key: String?, value: Long) = set(key!!, value)
        override fun putFloat(key: String?, value: Float) = set(key!!, value)
        override fun putBoolean(key: String?, value: Boolean) = set(key!!, value)

        private fun set(key: String, value: Any?): SharedPreferences.Editor {
            removes.remove(key)
            puts[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            puts.remove(key)
            removes.add(key!!)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            if (clear) map.clear()
            removes.forEach { map.remove(it) }
            puts.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
