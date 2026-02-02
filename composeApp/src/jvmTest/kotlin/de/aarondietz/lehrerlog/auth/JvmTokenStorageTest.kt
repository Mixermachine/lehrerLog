package de.aarondietz.lehrerlog.auth

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JvmTokenStorageTest {

    @Test
    fun savesAndClearsTokens() {
        val tempDir = Files.createTempDirectory("lehrerlog-tokens")
        val originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
        try {
            val storage = JvmTokenStorage()
            storage.saveAccessToken("access-token")
            storage.saveRefreshToken("refresh-token")

            assertEquals("access-token", storage.getAccessToken())
            assertEquals("refresh-token", storage.getRefreshToken())

            val reloaded = JvmTokenStorage()
            assertEquals("access-token", reloaded.getAccessToken())
            assertEquals("refresh-token", reloaded.getRefreshToken())

            reloaded.clearTokens()
            assertNull(reloaded.getAccessToken())
            assertNull(reloaded.getRefreshToken())

            assertNotNull(createTokenStorage())
        } finally {
            if (originalHome == null) {
                System.clearProperty("user.home")
            } else {
                System.setProperty("user.home", originalHome)
            }
        }
    }
}
