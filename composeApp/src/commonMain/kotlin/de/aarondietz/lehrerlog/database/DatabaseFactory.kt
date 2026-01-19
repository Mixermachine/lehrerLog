package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.db.SqlDriver
import de.aarondietz.lehrerlog.lehrerLog

/**
 * Factory for creating SQLDelight database instances.
 * Platform-specific implementations provide the SqlDriver.
 */
expect class DatabaseDriverFactory(context: Any?) {
    fun createDriver(): SqlDriver
    fun deleteDatabase()
}

/**
 * Create lehrerLog database instance.
 */
fun createDatabase(driverFactory: DatabaseDriverFactory): lehrerLog {
    val driver = driverFactory.createDriver()
    return lehrerLog(driver)
}

/**
 * Manages SQLDelight database lifecycle so it can be reset on logout.
 */
class DatabaseManager(private val driverFactory: DatabaseDriverFactory) {
    private var driver: SqlDriver? = null
    private var database: lehrerLog? = null

    val db: lehrerLog
        get() = getDatabase()

    @Synchronized
    fun getDatabase(): lehrerLog {
        val existing = database
        if (existing != null) {
            return existing
        }

        val createdDriver = driverFactory.createDriver()
        driver = createdDriver
        val createdDb = lehrerLog(createdDriver)
        database = createdDb
        return createdDb
    }

    @Synchronized
    fun reset() {
        close()
        driverFactory.deleteDatabase()
    }

    @Synchronized
    fun close() {
        driver?.close()
        driver = null
        database = null
    }
}
