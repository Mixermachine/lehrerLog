package de.aarondietz.lehrerlog.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import de.aarondietz.lehrerlog.lehrerLog
import org.w3c.dom.Worker

/**
 * WasmJS implementation of DatabaseDriverFactory.
 * Uses WebWorkerDriver for browser-based storage.
 */
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return WebWorkerDriver(
            Worker(
                js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")
            )
        ).also { driver ->
            lehrerLog.Schema.create(driver)
        }
    }
}
