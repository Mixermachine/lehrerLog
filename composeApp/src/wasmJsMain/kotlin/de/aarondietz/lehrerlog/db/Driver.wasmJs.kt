package de.aarondietz.lehrerlog.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker
import org.w3c.dom.WorkerOptions

@OptIn(ExperimentalWasmJsInterop::class)
private val workerUrl: String = js("""new URL("@cashapp/sqldelight-sqljs-worker/sqljs.worker.js", import.meta.url)""")

private val workerOptions: WorkerOptions = js("{ type: 'module' }")

actual suspend fun provideDbDriver(
    schema: SqlSchema<QueryResult.AsyncValue<Unit>>
): SqlDriver {
    return WebWorkerDriver(
        Worker(workerUrl, workerOptions)
    ).also { schema.create(it).await() }
}
