package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.db.SqlDriver
import de.aarondietz.lehrerlog.lehrerLog

/**
 * Factory for creating SQLDelight database instances.
 * Platform-specific implementations provide the SqlDriver.
 */
expect class DatabaseDriverFactory(context: Any?) {
    suspend fun createDriver(): SqlDriver
    fun deleteDatabase()
}

/**
 * Create lehrerLog database instance.
 */
suspend fun createDatabase(driverFactory: DatabaseDriverFactory): lehrerLog {
    val driver = driverFactory.createDriver()
    return lehrerLog(driver)
}

/**
 * Manages SQLDelight database lifecycle so it can be reset on logout.
 */
class DatabaseManager(private val driverFactory: DatabaseDriverFactory) {
    private var driver: SqlDriver? = null
    private var database: lehrerLog? = null
    private val lock = DatabaseLock()

    suspend fun getDatabase(): lehrerLog {
        return lock.withLock {
            val existing = database
            if (existing != null) {
                return@withLock existing
            }

            val createdDriver = driverFactory.createDriver()
            driver = createdDriver
            val createdDb = lehrerLog(createdDriver)
            database = createdDb
            createdDb
        }
    }

    suspend fun reset() {
        lock.withLock {
            closeInternal()
            driverFactory.deleteDatabase()
        }
    }

    suspend fun close() {
        lock.withLock { closeInternal() }
    }

    private fun closeInternal() {
        driver?.close()
        driver = null
        database = null
    }
}
