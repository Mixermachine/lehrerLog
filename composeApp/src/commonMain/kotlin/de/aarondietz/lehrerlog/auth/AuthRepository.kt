package de.aarondietz.lehrerlog.auth

import de.aarondietz.lehrerlog.SERVER_URL
import de.aarondietz.lehrerlog.auth.AuthResponse
import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.LoginRequest
import de.aarondietz.lehrerlog.auth.LogoutRequest
import de.aarondietz.lehrerlog.auth.RefreshRequest
import de.aarondietz.lehrerlog.auth.RegisterRequest
import de.aarondietz.lehrerlog.auth.TokenResponse
import de.aarondietz.lehrerlog.auth.UserDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val code: Int = 0) : AuthResult<Nothing>()
}

class AuthRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage
) {
    private val baseUrl = SERVER_URL

    suspend fun register(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        schoolCode: String? = null
    ): AuthResult<AuthResponse> {
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email, password, firstName, lastName, schoolCode))
            }

            when (response.status) {
                HttpStatusCode.Created -> {
                    val authResponse = response.body<AuthResponse>()
                    saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    AuthResult.Success(authResponse)
                }
                else -> {
                    val error = try {
                        response.body<ErrorResponse>()
                    } catch (e: Exception) {
                        ErrorResponse("Registration failed")
                    }
                    AuthResult.Error(error.error, response.status.value)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun login(email: String, password: String): AuthResult<AuthResponse> {
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(email, password))
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val authResponse = response.body<AuthResponse>()
                    saveTokens(authResponse.accessToken, authResponse.refreshToken)
                    AuthResult.Success(authResponse)
                }
                else -> {
                    val error = try {
                        response.body<ErrorResponse>()
                    } catch (e: Exception) {
                        ErrorResponse("Login failed")
                    }
                    AuthResult.Error(error.error, response.status.value)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun refreshToken(): AuthResult<TokenResponse> {
        val refreshToken = tokenStorage.getRefreshToken() ?: return AuthResult.Error("No refresh token")

        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(refreshToken))
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val tokenResponse = response.body<TokenResponse>()
                    saveTokens(tokenResponse.accessToken, tokenResponse.refreshToken)
                    AuthResult.Success(tokenResponse)
                }
                else -> {
                    clearTokens()
                    val error = try {
                        response.body<ErrorResponse>()
                    } catch (e: Exception) {
                        ErrorResponse("Token refresh failed")
                    }
                    AuthResult.Error(error.error, response.status.value)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    suspend fun logout(): AuthResult<Unit> {
        val refreshToken = tokenStorage.getRefreshToken() ?: return AuthResult.Success(Unit)
        val accessToken = tokenStorage.getAccessToken()

        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/auth/logout") {
                contentType(ContentType.Application.Json)
                accessToken?.let { header("Authorization", "Bearer $it") }
                setBody(LogoutRequest(refreshToken))
            }

            clearTokens()
            AuthResult.Success(Unit)
        } catch (e: Exception) {
            clearTokens()
            AuthResult.Success(Unit)
        }
    }

    suspend fun getCurrentUser(): AuthResult<UserDto> {
        val accessToken = tokenStorage.getAccessToken() ?: return AuthResult.Error("Not authenticated")

        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/auth/me") {
                header("Authorization", "Bearer $accessToken")
            }

            when (response.status) {
                HttpStatusCode.OK -> {
                    val user = response.body<UserDto>()
                    AuthResult.Success(user)
                }
                HttpStatusCode.Unauthorized -> {
                    // Try to refresh token
                    when (val refreshResult = refreshToken()) {
                        is AuthResult.Success -> getCurrentUser()
                        is AuthResult.Error -> AuthResult.Error("Session expired", 401)
                    }
                }
                else -> {
                    val error = try {
                        response.body<ErrorResponse>()
                    } catch (e: Exception) {
                        ErrorResponse("Failed to get user")
                    }
                    AuthResult.Error(error.error, response.status.value)
                }
            }
        } catch (e: Exception) {
            AuthResult.Error(e.message ?: "Network error")
        }
    }

    fun isLoggedIn(): Boolean {
        return tokenStorage.getAccessToken() != null
    }

    fun getAccessToken(): String? {
        return tokenStorage.getAccessToken()
    }

    private fun saveTokens(accessToken: String, refreshToken: String) {
        tokenStorage.saveAccessToken(accessToken)
        tokenStorage.saveRefreshToken(refreshToken)
    }

    private fun clearTokens() {
        tokenStorage.clearTokens()
    }
}
