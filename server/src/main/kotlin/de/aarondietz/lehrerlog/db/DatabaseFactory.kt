package de.aarondietz.lehrerlog.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)
    private var dataSource: HikariDataSource? = null

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

        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            driverClassName = driver
            username = dbUser
            password = dbPassword
            maximumPoolSize = envInt("DB_POOL_SIZE", 10)
            minimumIdle = envInt("DB_POOL_MIN_IDLE", 2)
            idleTimeout = envLong("DB_POOL_IDLE_TIMEOUT_MS", 600_000L)
            maxLifetime = envLong("DB_POOL_MAX_LIFETIME_MS", 1_800_000L)
            connectionTimeout = envLong("DB_POOL_CONN_TIMEOUT_MS", 10_000L)
            poolName = "lehrerlog-pool"
        }

        dataSource?.close()
        dataSource = HikariDataSource(config)

        logger.info("Initializing database connection pool")

        // Run Flyway migrations
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()

        flyway.migrate()

        // Connect Exposed
        Database.connect(dataSource!!)
    }

    private data class DatabaseConfig(
        val url: String,
        val driver: String,
        val user: String,
        val password: String
    )

    private fun envInt(name: String, default: Int): Int {
        return System.getenv(name)?.toIntOrNull()
            ?: System.getProperty(name)?.toIntOrNull()
            ?: default
    }

    private fun envLong(name: String, default: Long): Long {
        return System.getenv(name)?.toLongOrNull()
            ?: System.getProperty(name)?.toLongOrNull()
            ?: default
    }
}
