package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.db.SqlDriver
import de.aarondietz.lehrerlog.lehrerLog

/**
 * Factory for creating SQLDelight database instances.
 * Platform-specific implementations provide the SqlDriver.
 */
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Create lehrerLog database instance.
 */
fun createDatabase(driverFactory: DatabaseDriverFactory): lehrerLog {
    val driver = driverFactory.createDriver()
    return lehrerLog(driver)
}
