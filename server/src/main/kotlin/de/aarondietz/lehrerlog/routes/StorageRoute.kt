package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.services.StorageService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.storageRoute(
    storageService: StorageService = StorageService()
) {
    authenticate("jwt") {
        route("/api/storage") {
            get("/quota") {
                val principal = call.getPrincipalOrRespond()
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
                val principal = call.getPrincipalOrRespond()
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
