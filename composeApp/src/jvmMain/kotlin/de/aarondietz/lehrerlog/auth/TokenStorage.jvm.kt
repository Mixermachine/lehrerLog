package de.aarondietz.lehrerlog.auth

import java.io.File
import java.util.Properties

/**
 * JVM/Desktop TokenStorage implementation using a properties file.
 * TODO: Consider using system keychain or encrypted storage for production.
 */
class JvmTokenStorage : TokenStorage {
    private val tokenFile: File
    private val properties = Properties()

    init {
        val userHome = System.getProperty("user.home")
        val appDir = File(userHome, ".lehrerlog")
        appDir.mkdirs()
        tokenFile = File(appDir, "tokens.properties")

        if (tokenFile.exists()) {
            tokenFile.inputStream().use { properties.load(it) }
        }
    }

    override fun saveAccessToken(token: String) {
        properties.setProperty(KEY_ACCESS_TOKEN, token)
        save()
    }

    override fun getAccessToken(): String? {
        return properties.getProperty(KEY_ACCESS_TOKEN)
    }

    override fun saveRefreshToken(token: String) {
        properties.setProperty(KEY_REFRESH_TOKEN, token)
        save()
    }

    override fun getRefreshToken(): String? {
        return properties.getProperty(KEY_REFRESH_TOKEN)
    }

    override fun clearTokens() {
        properties.remove(KEY_ACCESS_TOKEN)
        properties.remove(KEY_REFRESH_TOKEN)
        save()
    }

    private fun save() {
        tokenFile.outputStream().use { properties.store(it, "LehrerLog Auth Tokens") }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}

actual fun createTokenStorage(): TokenStorage = JvmTokenStorage()
