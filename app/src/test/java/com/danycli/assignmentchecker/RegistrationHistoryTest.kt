package com.danycli.assignmentchecker

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RegistrationHistoryTest {

    private lateinit var fakePrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        RegistrationHistoryStore.getPrefs = { fakePrefs }
    }

    @Test
    fun testGetSavedRegistrationsEmpty() {
        val list = RegistrationHistoryStore.getSavedRegistrations(null)
        assertTrue(list.isEmpty())
    }

    @Test
    fun testSaveRegistrationAndDeduplicate() {
        // Save first registration
        RegistrationHistoryStore.saveRegistration(null, "ciit/sp25-bcs-136/atd")
        
        // Save second registration
        RegistrationHistoryStore.saveRegistration(null, "CIIT/SP24-BSE-102/ATD")
        
        // Save first registration again (should deduplicate and move to top)
        RegistrationHistoryStore.saveRegistration(null, "ciit/sp25-bcs-136/atd ")

        val list = RegistrationHistoryStore.getSavedRegistrations(null)
        assertEquals(2, list.size)
        // SP25 should be at the top
        assertEquals("CIIT/SP25-BCS-136/ATD", list[0])
        assertEquals("CIIT/SP24-BSE-102/ATD", list[1])
    }

    @Test
    fun testSaveRegistrationLimit() {
        // Save 12 accounts
        for (i in 1..12) {
            RegistrationHistoryStore.saveRegistration(null, "CIIT/SP25-BCS-$i/ATD")
        }

        val list = RegistrationHistoryStore.getSavedRegistrations(null)
        // Should limit to max 10
        assertEquals(10, list.size)
        
        // Most recently added (12) should be at the top, and oldest (1 and 2) should be removed
        assertEquals("CIIT/SP25-BCS-12/ATD", list[0])
        assertEquals("CIIT/SP25-BCS-3/ATD", list[9])
    }

    @Test
    fun testRemoveRegistration() {
        RegistrationHistoryStore.saveRegistration(null, "CIIT/SP25-BCS-136/ATD")
        RegistrationHistoryStore.saveRegistration(null, "CIIT/SP24-BSE-102/ATD")

        // Remove SP25
        RegistrationHistoryStore.removeRegistration(null, "CIIT/SP25-BCS-136/ATD")

        val list = RegistrationHistoryStore.getSavedRegistrations(null)
        assertEquals(1, list.size)
        assertEquals("CIIT/SP24-BSE-102/ATD", list[0])
    }

    @Test
    fun testClearAll() {
        RegistrationHistoryStore.saveRegistration(null, "CIIT/SP25-BCS-136/ATD")
        RegistrationHistoryStore.clearAll(null)

        val list = RegistrationHistoryStore.getSavedRegistrations(null)
        assertTrue(list.isEmpty())
    }
}

class FakeSharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = map
    override fun getString(key: String, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = map[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class Editor : SharedPreferences.Editor {
        private val tempMap = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            tempMap[key] = value
            return this
        }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            tempMap[key] = values
            return this
        }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            tempMap[key] = value
            return this
        }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            tempMap[key] = value
            return this
        }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            tempMap[key] = value
            return this
        }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            tempMap[key] = value
            return this
        }
        override fun remove(key: String): SharedPreferences.Editor {
            toRemove.add(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            tempMap.clear()
            toRemove.addAll(map.keys)
            return this
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            toRemove.forEach { map.remove(it) }
            tempMap.forEach { (k, v) -> map[k] = v }
        }
    }
}
