package com.danycli.assignmentchecker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.lang.reflect.Method

class AppUpdateCheckerTest {

    private fun getPrivateMethod(name: String, vararg parameterTypes: Class<*>): Method {
        val clazz = Class.forName("com.danycli.assignmentchecker.AppUpdateCheckerKt")
        val method = clazz.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return method
    }

    @Test
    fun testExtractReleaseVersionCode() {
        val method = getPrivateMethod("extractReleaseVersionCode", String::class.java)
        
        fun extract(value: String?) = method.invoke(null, value) as? Int

        assertEquals(12, extract("vc12"))
        assertEquals(12, extract("Version Code: 12"))
        assertEquals(12, extract("vc: 12"))
        assertEquals(12, extract("Release vc-12"))
        assertEquals(12, extract("VersionCode#12"))
        assertEquals(null, extract("No version here"))
    }
}
