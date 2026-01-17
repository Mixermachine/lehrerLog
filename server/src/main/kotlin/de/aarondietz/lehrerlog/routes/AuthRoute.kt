package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.*
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val firstName: String,
    val lastName: String,
    val schoolCode: String? = null
)

@Serializable
data class RefreshRequestDto(
    val refreshToken: String
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String
)

@Serializable
data class JoinSchoolRequestDto(
    val schoolCode: String
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val schoolId: String?
)

fun Route.authRoute(authService: AuthService) {
    route("/auth") {
        // Public endpoints
        post("/register") {
            try {
                val request = call.receive<RegisterRequestDto>()

                // Validate input
                if (request.email.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email and password are required"))
                    return@post
                }
                if (request.password.length < 8) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Password must be at least 8 characters"))
                    return@post
                }
                if (request.firstName.isBlank() || request.lastName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("First name and last name are required"))
                    return@post
                }

                val deviceInfo = call.request.header("User-Agent")
                val (tokens, user) = authService.register(
                    RegisterRequest(
                        email = request.email,
                        password = request.password,
                        firstName = request.firstName,
                        lastName = request.lastName,
                        schoolCode = request.schoolCode
                    ),
                    deviceInfo
                )

                call.respond(HttpStatusCode.Created, AuthResponseDto(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresIn = tokens.expiresIn,
                    user = user.toDto()
                ))
            } catch (e: AuthException) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Registration failed"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Registration failed: ${e.message}"))
            }
        }

        post("/login") {
            try {
                val request = call.receive<LoginRequestDto>()

                if (request.email.isBlank() || request.password.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Email and password are required"))
                    return@post
                }

                val deviceInfo = call.request.header("User-Agent")
                val (tokens, user) = authService.login(
                    LoginRequest(request.email, request.password),
                    deviceInfo
                )

                call.respond(AuthResponseDto(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresIn = tokens.expiresIn,
                    user = user.toDto()
                ))
            } catch (e: AuthException) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(e.message ?: "Invalid credentials"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Login failed: ${e.message}"))
            }
        }

        post("/refresh") {
            try {
                val request = call.receive<RefreshRequestDto>()

                if (request.refreshToken.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Refresh token is required"))
                    return@post
                }

                val deviceInfo = call.request.header("User-Agent")
                val tokens = authService.refresh(request.refreshToken, deviceInfo)

                call.respond(mapOf(
                    "accessToken" to tokens.accessToken,
                    "refreshToken" to tokens.refreshToken,
                    "expiresIn" to tokens.expiresIn
                ))
            } catch (e: AuthException) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(e.message ?: "Invalid refresh token"))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Token refresh failed: ${e.message}"))
            }
        }

        // Authenticated endpoints
        authenticate("jwt") {
            post("/logout") {
                try {
                    val request = call.receive<LogoutRequestDto>()
                    authService.logout(request.refreshToken)
                    call.respond(SuccessResponse("Logged out successfully"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Logout failed: ${e.message}"))
                }
            }

            post("/logout-all") {
                try {
                    val principal = call.principal<UserPrincipal>()!!
                    val count = authService.logoutAll(principal.id)
                    call.respond(SuccessResponse("Logged out from $count devices"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Logout failed: ${e.message}"))
                }
            }

            post("/join-school") {
                try {
                    val request = call.receive<JoinSchoolRequestDto>()
                    if (request.schoolCode.isBlank()) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("School code is required"))
                        return@post
                    }

                    val principal = call.principal<UserPrincipal>()!!
                    val deviceInfo = call.request.header("User-Agent")
                    val (tokens, user) = authService.joinSchool(
                        userId = principal.id,
                        schoolCode = request.schoolCode.trim(),
                        deviceInfo = deviceInfo
                    )

                    call.respond(AuthResponseDto(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = tokens.expiresIn,
                        user = user.toDto()
                    ))
                } catch (e: AuthException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Failed to join school"))
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to join school: ${e.message}"))
                }
            }

            get("/me") {
                try {
                    val principal = call.principal<UserPrincipal>()!!
                    val user = authService.getUserById(principal.id)

                    if (user != null) {
                        call.respond(user.toDto())
                    } else {
                        call.respond(HttpStatusCode.NotFound, ErrorResponse("User not found"))
                    }
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Failed to get user: ${e.message}"))
                }
            }
        }
    }
}

private fun UserInfo.toDto() = UserDto(
    id = id.toString(),
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = role.name,
    schoolId = schoolId?.toString()
)
