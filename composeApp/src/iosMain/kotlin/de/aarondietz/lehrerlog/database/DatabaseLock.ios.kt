package de.aarondietz.lehrerlog.database

import platform.Foundation.NSLock

actual class DatabaseLock {
    private val lock = NSLock()

    actual fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}
