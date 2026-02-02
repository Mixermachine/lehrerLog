package de.aarondietz.lehrerlog

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmPlatformActualsTest {

    @Test
    fun isDebugBuildReadsEnvProperty() {
        val original = System.getProperty("env")
        try {
            System.setProperty("env", "development")
            assertTrue(isDebugBuild())

            System.setProperty("env", "production")
            assertFalse(isDebugBuild())
        } finally {
            if (original == null) {
                System.clearProperty("env")
            } else {
                System.setProperty("env", original)
            }
        }
    }

    @Test
    fun currentTimeMillisMatchesSystemClock() {
        val before = System.currentTimeMillis()
        val actual = currentTimeMillis()
        val after = System.currentTimeMillis()
        assertTrue(actual in before..after)
    }
}
