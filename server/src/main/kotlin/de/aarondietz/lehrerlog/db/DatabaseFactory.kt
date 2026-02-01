package de.aarondietz.lehrerlog.db

import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

object DatabaseFactory {

    fun init() {
        val dbMode = System.getenv("DB_MODE") ?: "h2" // "h2" for dev, "postgres" for prod

        val (jdbcUrl, driver, dbUser, dbPassword) = when (dbMode.lowercase()) {
            "postgres", "postgresql" -> {
                val url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/lehrerlog"
                val user = System.getenv("DATABASE_USER") ?: "postgres"
                val password = System.getenv("DATABASE_PASSWORD") ?: "postgres"
                DatabaseConfig(url, "org.postgresql.Driver", user, password)
            }

            else -> { // Default to H2 for development
                val url = System.getenv("DATABASE_URL")
                    ?: "jdbc:h2:./build/lehrerlog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
                DatabaseConfig(url, "org.h2.Driver", "", "")
            }
        }

        println("Initializing database: $jdbcUrl")

        // Run Flyway migrations
        val flyway = Flyway.configure()
            .dataSource(jdbcUrl, dbUser, dbPassword)
            .locations("classpath:db/migration")
            .load()

        flyway.migrate()

        // Connect Exposed
        Database.connect(
            url = jdbcUrl,
            driver = driver,
            user = dbUser,
            password = dbPassword
        )
    }

    private data class DatabaseConfig(
        val url: String,
        val driver: String,
        val user: String,
        val password: String
    )
}
