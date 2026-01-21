package de.aarondietz.lehrerlog.database

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

actual class DatabaseLock {
    private val mutex = Mutex()

    actual suspend fun <T> withLock(block: suspend () -> T): T {
        return mutex.withLock {
            block()
        }
    }
}
