package de.aarondietz.lehrerlog.auth

object JwtConfig {
    val secret: String = System.getenv("JWT_SECRET") ?: "dev-secret-change-in-production"
    val issuer: String = System.getenv("JWT_ISSUER") ?: "lehrerlog"
    val audience: String = System.getenv("JWT_AUDIENCE") ?: "lehrerlog-users"
    const val REALM = "LehrerLog"

    const val ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000L // 15 minutes
    const val REFRESH_TOKEN_VALIDITY_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
}
