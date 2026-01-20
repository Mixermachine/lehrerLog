package de.aarondietz.lehrerlog.database

actual class DatabaseLock {
    actual fun <T> withLock(block: () -> T): T = block()
}
