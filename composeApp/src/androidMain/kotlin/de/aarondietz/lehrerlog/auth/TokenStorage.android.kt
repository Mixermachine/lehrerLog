package de.aarondietz.lehrerlog.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Android TokenStorage implementation using SharedPreferences.
 * TODO: Upgrade to EncryptedSharedPreferences for production.
 */
class AndroidTokenStorage(context: Context) : TokenStorage {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "auth_tokens",
        Context.MODE_PRIVATE
    )

    override fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }

    override fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }

    override fun saveRefreshToken(token: String) {
        prefs.edit().putString(KEY_REFRESH_TOKEN, token).apply()
    }

    override fun getRefreshToken(): String? {
        return prefs.getString(KEY_REFRESH_TOKEN, null)
    }

    override fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
    }
}

private var androidContext: Context? = null

fun initAndroidTokenStorage(context: Context) {
    androidContext = context.applicationContext
}

actual fun createTokenStorage(): TokenStorage {
    val context = androidContext
        ?: throw IllegalStateException("Android context not initialized. Call initAndroidTokenStorage() first.")
    return AndroidTokenStorage(context)
}
