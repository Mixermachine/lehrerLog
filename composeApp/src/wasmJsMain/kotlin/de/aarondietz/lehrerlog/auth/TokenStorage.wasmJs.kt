package de.aarondietz.lehrerlog.auth

import kotlinx.browser.localStorage

/**
 * Web TokenStorage implementation using localStorage.
 * Note: localStorage is not secure for sensitive data in production.
 * Consider using HttpOnly cookies managed by the server for better security.
 */
class WebTokenStorage : TokenStorage {
    override fun saveAccessToken(token: String) {
        localStorage.setItem(KEY_ACCESS_TOKEN, token)
    }

    override fun getAccessToken(): String? {
        return localStorage.getItem(KEY_ACCESS_TOKEN)
    }

    override fun saveRefreshToken(token: String) {
        localStorage.setItem(KEY_REFRESH_TOKEN, token)
    }

    override fun getRefreshToken(): String? {
        return localStorage.getItem(KEY_REFRESH_TOKEN)
    }

    override fun clearTokens() {
        localStorage.removeItem(KEY_ACCESS_TOKEN)
        localStorage.removeItem(KEY_REFRESH_TOKEN)
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "lehrerlog_access_token"
        private const val KEY_REFRESH_TOKEN = "lehrerlog_refresh_token"
    }
}

actual fun createTokenStorage(): TokenStorage = WebTokenStorage()
