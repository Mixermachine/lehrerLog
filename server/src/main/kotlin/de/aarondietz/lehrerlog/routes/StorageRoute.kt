package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.services.StorageService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

fun Route.storageRoute(
    storageService: StorageService = StorageService()
) {
    authenticate("jwt") {
        route("/api/storage") {
            get("/quota") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                try {
                    val quota = storageService.getQuota(principal.id, schoolId)
                    call.respond(quota)
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Storage subscription not found"))
                }
            }

            get("/usage") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                try {
                    val usage = storageService.getUsage(principal.id, schoolId)
                    call.respond(usage)
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Storage subscription not found"))
                }
            }
        }
    }
}
