package de.aarondietz.lehrerlog.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

@OptIn(ExperimentalWasmJsInterop::class)
private val workerUrl: String = js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")

actual suspend fun provideDbDriver(
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>
): SqlDriver {
    return WebWorkerDriver(
        Worker(workerUrl)
    ).also { schema.create(it).await() }
}
