package de.aarondietz.lehrerlog.database
 
 import android.content.Context
 import app.cash.sqldelight.db.QueryResult
 import app.cash.sqldelight.db.SqlDriver
 import app.cash.sqldelight.db.SqlSchema
 import app.cash.sqldelight.driver.android.AndroidSqliteDriver
 import de.aarondietz.lehrerlog.lehrerLog
  
  /**
   * Android implementation of DatabaseDriverFactory.
   * Uses AndroidSqliteDriver with application context.
   */
  actual class DatabaseDriverFactory(private val context: Context) {
      actual fun createDriver(): SqlDriver {
          return AndroidSqliteDriver(
              lehrerLog.Schema,    // First: schema (no parameter name)
              context,             // Second: context (no parameter name)
              "lehrerlog.db"       // Third: database name (no parameter name)
          )
      }
  }