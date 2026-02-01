package de.aarondietz.lehrerlog.util

import org.slf4j.LoggerFactory

object EnvironmentValidator {
    private val logger = LoggerFactory.getLogger(EnvironmentValidator::class.java)

    /**
     * Validates that all required environment variables are set.
     * Throws IllegalStateException if any required variables are missing.
     *
     * Should be called early in application startup before any services are initialized.
     *
     * Note: JWT_SECRET is validated separately in JwtConfig with a warning if using default.
     */
    fun validateRequiredEnvVars() {
        val dbMode = System.getenv("DB_MODE")?.lowercase() ?: "h2"

        val required = mutableListOf<String>()

        // Database credentials only required for PostgreSQL mode
        if (dbMode in listOf("postgres", "postgresql")) {
            required.addAll(listOf("DATABASE_URL", "DATABASE_USER", "DATABASE_PASSWORD"))
        }

        val missing = required.filter { System.getenv(it).isNullOrBlank() }

        if (missing.isNotEmpty()) {
            val error = "Missing required environment variables: ${missing.joinToString()}"
            logger.error(error)
            error(error)
        }

        logger.info("Environment validation passed - all required variables are set")
    }
}
