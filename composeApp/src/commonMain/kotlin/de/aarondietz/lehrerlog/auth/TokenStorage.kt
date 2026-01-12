package de.aarondietz.lehrerlog.auth

/**
 * Interface for secure token storage across platforms.
 * Platform-specific implementations should use appropriate secure storage:
 * - Android: EncryptedSharedPreferences
 * - iOS: Keychain
 * - Desktop: Encrypted file or system keychain
 * - Web: localStorage (with caveats about security)
 */
interface TokenStorage {
    fun saveAccessToken(token: String)
    fun getAccessToken(): String?
    fun saveRefreshToken(token: String)
    fun getRefreshToken(): String?
    fun clearTokens()
}
