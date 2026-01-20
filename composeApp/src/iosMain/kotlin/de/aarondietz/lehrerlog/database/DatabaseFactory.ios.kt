package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import de.aarondietz.lehrerlog.lehrerLog
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

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

    @OptIn(ExperimentalForeignApi::class)
    actual fun deleteDatabase() {
        val fileManager = NSFileManager.defaultManager
        val urls = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
        val baseUrl = urls.firstOrNull()
        val basePath = baseUrl?.path ?: return
        val dbPath = "$basePath/lehrerlog.db"
        val walPath = "$basePath/lehrerlog.db-wal"
        val shmPath = "$basePath/lehrerlog.db-shm"

        fileManager.removeItemAtPath(dbPath, null)
        fileManager.removeItemAtPath(walPath, null)
        fileManager.removeItemAtPath(shmPath, null)
    }
}
