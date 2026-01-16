package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.services.SyncService
import de.aarondietz.lehrerlog.sync.PushChangesRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Routes for synchronization endpoints.
 * Handles pulling changes from server and pushing local changes.
 */
fun Route.syncRoute(syncService: SyncService = SyncService()) {
    authenticate("jwt") {
        route("/api/sync") {

            /**
             * GET /api/sync/changes?since={logId}
             * Pull changes from the server since a specific sync log ID.
             */
            get("/changes") {
                val principal = call.principal<UserPrincipal>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized)

                val schoolId = principal.schoolId
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "User not associated with a school")
                    )

                val sinceLogId = call.request.queryParameters["since"]?.toLongOrNull()
                    ?: return@get call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Missing or invalid 'since' parameter")
                    )

                try {
                    val response = syncService.getChangesSince(schoolId, sinceLogId)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Failed to fetch changes"))
                    )
                }
            }

            /**
             * POST /api/sync/push
             * Push local changes to the server.
             */
            post("/push") {
                val principal = call.principal<UserPrincipal>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                val schoolId = principal.schoolId
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "User not associated with a school")
                    )

                try {
                    val request = call.receive<PushChangesRequest>()
                    val response = syncService.pushChanges(schoolId, principal.id, request)
                    call.respond(HttpStatusCode.OK, response)
                } catch (e: Exception) {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to (e.message ?: "Failed to push changes"))
                    )
                }
            }
        }
    }
}
