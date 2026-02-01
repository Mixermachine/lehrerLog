package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.UserPrincipal
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.response.*

/**
 * Safely retrieves the authenticated UserPrincipal.
 * Responds with 401 Unauthorized and throws BadRequestException if principal is not available.
 *
 * Usage in route handlers:
 * ```
 * val principal = call.getPrincipalOrRespond()
 * ```
 *
 * Note: This function throws BadRequestException after responding, ensuring execution stops immediately.
 */
suspend fun ApplicationCall.getPrincipalOrRespond(): UserPrincipal {
    val principal = principal<UserPrincipal>()
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized, ErrorResponse("Authentication required"))
        throw BadRequestException("Principal not found")
    }
    return principal
}
