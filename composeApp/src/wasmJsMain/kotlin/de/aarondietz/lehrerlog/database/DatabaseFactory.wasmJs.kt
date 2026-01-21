package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import de.aarondietz.lehrerlog.lehrerLog
import org.w3c.dom.Worker

/**
 * WasmJS implementation of DatabaseDriverFactory.
 * Uses WebWorkerDriver for browser-based storage.
 */
private val sqljsWorkerUrl: String = js(
    "new URL('@cashapp/sqldelight-sqljs-worker/sqljs.worker.js', import.meta.url).toString()"
)

actual class DatabaseDriverFactory actual constructor(context: Any?) {
    actual suspend fun createDriver(): SqlDriver {
        val driver = WebWorkerDriver(
            Worker(
                sqljsWorkerUrl
            )
        )
        // Await async schema creation - WebWorkerDriver is inherently async
        lehrerLog.Schema.create(driver).await()
        return driver
    }

    actual fun deleteDatabase() {
        // SQL.js storage is ephemeral per session; nothing to delete here.
    }
}
