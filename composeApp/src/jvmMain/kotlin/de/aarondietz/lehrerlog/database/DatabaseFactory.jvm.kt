package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.aarondietz.lehrerlog.lehrerLog
import java.io.File

/**
 * JVM/Desktop implementation of DatabaseDriverFactory.
 * Uses JdbcSqliteDriver with file-based database.
 */
actual class DatabaseDriverFactory actual constructor(context: Any?) {
    actual fun createDriver(): SqlDriver {
        val databasePath = File(System.getProperty("user.home"), ".lehrerlog")
        databasePath.mkdirs()

        val dbFile = File(databasePath, "lehrerlog.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // Always create schema - CREATE TABLE IF NOT EXISTS will not recreate existing tables
        lehrerLog.Schema.synchronous().create(driver)

        return driver
    }
}
