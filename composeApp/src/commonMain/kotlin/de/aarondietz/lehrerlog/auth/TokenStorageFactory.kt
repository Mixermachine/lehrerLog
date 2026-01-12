package de.aarondietz.lehrerlog.auth

/**
 * Factory function to create platform-specific TokenStorage.
 */
expect fun createTokenStorage(): TokenStorage
