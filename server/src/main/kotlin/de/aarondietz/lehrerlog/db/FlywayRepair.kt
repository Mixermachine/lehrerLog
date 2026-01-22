package de.aarondietz.lehrerlog.db

import org.flywaydb.core.Flyway

object FlywayRepair {
    @JvmStatic
    fun main(args: Array<String>) {
        val dbMode = System.getenv("DB_MODE") ?: "h2"

        val (jdbcUrl, dbUser, dbPassword) = when (dbMode.lowercase()) {
            "postgres", "postgresql" -> {
                val url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/lehrerlog"
                val user = System.getenv("DATABASE_USER") ?: "postgres"
                val password = System.getenv("DATABASE_PASSWORD") ?: "postgres"
                Triple(url, user, password)
            }
            else -> {
                val url = System.getenv("DATABASE_URL")
                    ?: "jdbc:h2:./build/lehrerlog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
                Triple(url, "", "")
            }
        }

        println("Running Flyway repair on: $jdbcUrl")

        val flyway = Flyway.configure()
            .dataSource(jdbcUrl, dbUser, dbPassword)
            .locations("classpath:db/migration")
            .load()

        flyway.repair()
        println("Flyway repair completed.")
    }
}
