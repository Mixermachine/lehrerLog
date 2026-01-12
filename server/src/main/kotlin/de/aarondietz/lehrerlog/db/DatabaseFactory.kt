package de.aarondietz.lehrerlog.db

import de.aarondietz.lehrerlog.db.tables.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init() {
        val jdbcUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/lehrerlog"
        val dbUser = System.getenv("DATABASE_USER") ?: "postgres"
        val dbPassword = System.getenv("DATABASE_PASSWORD") ?: "postgres"

        // Run Flyway migrations
        val flyway = Flyway.configure()
            .dataSource(jdbcUrl, dbUser, dbPassword)
            .locations("classpath:db/migration")
            .load()

        flyway.migrate()

        // Connect Exposed
        Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver",
            user = dbUser,
            password = dbPassword
        )

        // Verify tables exist (optional - Flyway should handle this)
        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                Schools,
                Users,
                RefreshTokens,
                Students,
                SchoolClasses,
                SyncLog
            )
        }
    }
}
