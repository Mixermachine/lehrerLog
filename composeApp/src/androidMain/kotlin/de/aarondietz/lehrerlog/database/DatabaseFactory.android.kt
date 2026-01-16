package de.aarondietz.lehrerlog.database
 
import android.content.Context
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import de.aarondietz.lehrerlog.lehrerLog
 
 /**
  * Android implementation of DatabaseDriverFactory.
  * Uses AndroidSqliteDriver with application context.
  */
  actual class DatabaseDriverFactory actual constructor(private val context: Any?) {
      actual fun createDriver(): SqlDriver {
          val androidContext = context as? Context
              ?: error("Android context is required to create the SQLDelight driver.")
          return AndroidSqliteDriver(
              lehrerLog.Schema.synchronous(), // First: schema (sync wrapper)
              androidContext, // Second: context (no parameter name)
              "lehrerlog.db"       // Third: database name (no parameter name)
          )
      }
  }
