package de.aarondietz.lehrerlog.auth

import platform.Foundation.NSUserDefaults

/**
 * iOS TokenStorage implementation using NSUserDefaults.
 * TODO: Upgrade to Keychain for production security.
 */
class NativeTokenStorage : TokenStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun saveAccessToken(token: String) {
        defaults.setObject(token, KEY_ACCESS_TOKEN)
    }

    override fun getAccessToken(): String? {
        return defaults.stringForKey(KEY_ACCESS_TOKEN)
    }

    override fun saveRefreshToken(token: String) {
        defaults.setObject(token, KEY_REFRESH_TOKEN)
    }

    override fun getRefreshToken(): String? {
        return defaults.stringForKey(KEY_REFRESH_TOKEN)
    }

    override fun clearTokens() {
        defaults.removeObjectForKey(KEY_ACCESS_TOKEN)
        defaults.removeObjectForKey(KEY_REFRESH_TOKEN)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "lehrerlog_access_token"
        private const val KEY_REFRESH_TOKEN = "lehrerlog_refresh_token"
    }
}

actual fun createTokenStorage(): TokenStorage = NativeTokenStorage()
