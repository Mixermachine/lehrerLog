package de.aarondietz.lehrerlog.auth

import org.slf4j.LoggerFactory

object JwtConfig {
    private val logger = LoggerFactory.getLogger(JwtConfig::class.java)

    private val environment = System.getenv("ENVIRONMENT")?.lowercase() ?: "dev"

    val secret: String = System.getenv("JWT_SECRET") ?: run {
        if (environment in listOf("qa", "prod", "production")) {
            val error = "JWT_SECRET environment variable is required for $environment environment"
            logger.error(error)
            error(error)
        }
        logger.warn("⚠️  Using default JWT_SECRET - THIS IS NOT SAFE FOR PRODUCTION!")
        "dev-secret-change-in-production"
    }
    val issuer: String = System.getenv("JWT_ISSUER") ?: "lehrerlog"
    val audience: String = System.getenv("JWT_AUDIENCE") ?: "lehrerlog-users"
    const val REALM = "LehrerLog"

    const val ACCESS_TOKEN_VALIDITY_MS = 60 * 60 * 1000L // 60 minutes
    const val REFRESH_TOKEN_VALIDITY_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
}
