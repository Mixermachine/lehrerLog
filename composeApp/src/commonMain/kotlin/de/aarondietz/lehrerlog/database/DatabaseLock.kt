package de.aarondietz.lehrerlog.database

/**
 * Platform lock used to guard database lifecycle operations.
 */
expect class DatabaseLock() {
    fun <T> withLock(block: () -> T): T
}
