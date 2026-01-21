package de.aarondietz.lehrerlog.database

/**
 * Platform lock used to guard database lifecycle operations.
 */
expect class DatabaseLock() {
    suspend fun <T> withLock(block: suspend () -> T): T
}
