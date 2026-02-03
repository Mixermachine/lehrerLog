package de.aarondietz.lehrerlog.network

import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.AuthResult
import io.ktor.client.plugins.*
import io.ktor.http.*

/**
 * Executes a block of code that makes an HTTP request, with automatic token refresh on 401.
 *
 * If the request fails with 401 Unauthorized:
 * 1. Attempts to refresh the access token using AuthRepository
 * 2. Retries the request once with the new token
 * 3. If refresh fails or retry still fails, throws the exception
 *
 * This prevents infinite retry loops by only retrying once.
 *
 * Usage in repositories:
 * ```
 * suspend fun getData(): Result<DataDto> {
 *     return withTokenRefresh(authRepository) {
 *         httpClient.get("$baseUrl/api/data") {
 *             tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
 *         }.body<DataDto>()
 *     }
 * }
 * ```
 */
suspend inline fun <T> withTokenRefresh(
    authRepository: AuthRepository?,
    crossinline block: suspend () -> T
): T {
    return try {
        block()
    } catch (e: ClientRequestException) {
        // If 401 Unauthorized and we have an authRepository, try to refresh and retry once
        if (e.response.status == HttpStatusCode.Unauthorized && authRepository != null) {
            when (authRepository.refreshToken()) {
                is AuthResult.Success -> {
                    // Token refreshed successfully, retry the request once
                    try {
                        block()
                    } catch (retryException: Exception) {
                        // If retry also fails, throw the retry exception
                        throw retryException
                    }
                }
                is AuthResult.Error -> {
                    // Refresh failed, throw original exception
                    throw e
                }
            }
        } else {
            // Not a 401 or no auth repository, throw original exception
            throw e
        }
    }
}
