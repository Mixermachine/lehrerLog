package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import de.aarondietz.lehrerlog.lehrerLog

/**
 * iOS implementation of DatabaseDriverFactory.
 * Uses NativeSqliteDriver for iOS/Native platforms.
 */
actual class DatabaseDriverFactory actual constructor(context: Any?) {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = lehrerLog.Schema.synchronous(),
            name = "lehrerlog.db"
        )
    }
}
