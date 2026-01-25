package de.aarondietz.lehrerlog.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val schoolCode: String? = null
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class LogoutRequest(
    val refreshToken: String
)

@Serializable
data class JoinSchoolRequest(
    val schoolCode: String
)

@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val schoolId: String? = null
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class SuccessResponse(
    val message: String
)

enum class UserRole {
    ADMIN,
    SCHOOL_ADMIN,
    TEACHER,
    PARENT
}
