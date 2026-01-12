package de.aarondietz.lehrerlog.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import de.aarondietz.lehrerlog.db.tables.UserRole
import java.util.*

data class UserPrincipal(
    val id: UUID,
    val email: String,
    val role: UserRole,
    val schoolId: UUID?
)

class TokenService {

    private val algorithm = Algorithm.HMAC256(JwtConfig.secret)

    fun generateAccessToken(
        userId: UUID,
        email: String,
        role: UserRole,
        schoolId: UUID?
    ): String {
        return JWT.create()
            .withSubject(userId.toString())
            .withClaim("email", email)
            .withClaim("role", role.name)
            .withClaim("schoolId", schoolId?.toString())
            .withIssuer(JwtConfig.issuer)
            .withAudience(JwtConfig.audience)
            .withExpiresAt(Date(System.currentTimeMillis() + JwtConfig.ACCESS_TOKEN_VALIDITY_MS))
            .sign(algorithm)
    }

    fun generateRefreshToken(): String {
        return UUID.randomUUID().toString()
    }

    fun hashRefreshToken(token: String): String {
        return org.mindrot.jbcrypt.BCrypt.hashpw(token, org.mindrot.jbcrypt.BCrypt.gensalt())
    }

    fun verifyRefreshToken(token: String, hash: String): Boolean {
        return org.mindrot.jbcrypt.BCrypt.checkpw(token, hash)
    }

    fun getJwtVerifier() = JWT
        .require(algorithm)
        .withIssuer(JwtConfig.issuer)
        .withAudience(JwtConfig.audience)
        .build()
}
