package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.AuthException
import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.UserInfo
import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.data.ParentInviteCreateRequest
import de.aarondietz.lehrerlog.data.ParentInviteRedeemRequest
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.services.ParentService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.parentRoute(parentService: ParentService = ParentService()) {
    route("/api/parent-invites") {
        post("/redeem") {
            val request = call.receive<ParentInviteRedeemRequest>()
            val deviceInfo = call.request.headers["User-Agent"]
            try {
                val (tokens, user) = parentService.redeemInvite(request, deviceInfo)
                call.respond(
                    AuthResponseDto(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        expiresIn = tokens.expiresIn,
                        user = user.toDto()
                    )
                )
            } catch (e: AuthException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invite redemption failed"))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("Invite redemption failed: ${e.message}")
                )
            }
        }
    }

    authenticate("jwt") {
        route("/api/parent-invites") {
            post {
                val principal = call.principal<UserPrincipal>()!!
                if (!principal.role.isTeacherRole()) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only teachers can create invites"))
                    return@post
                }
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val request = call.receive<ParentInviteCreateRequest>()
                try {
                    val response = parentService.createInvite(principal.id, schoolId, request)
                    call.respond(HttpStatusCode.Created, response)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                }
            }
        }

        route("/api/parent-links") {
            get {
                val principal = call.principal<UserPrincipal>()!!
                if (!principal.role.isTeacherRole()) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only teachers can view links"))
                    return@get
                }
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val studentId = call.request.queryParameters["studentId"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("studentId is required"))
                    return@get
                }

                try {
                    val links = parentService.listLinks(studentId, schoolId)
                    call.respond(links)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Student not found"))
                }
            }

            post("/{id}/revoke") {
                val principal = call.principal<UserPrincipal>()!!
                if (!principal.role.isTeacherRole()) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only teachers can revoke links"))
                    return@post
                }
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val linkId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid link ID"))
                    return@post
                }

                val revoked = parentService.revokeLink(linkId, schoolId, principal.id)
                if (revoked) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Link not found"))
                }
            }
        }

        route("/api/parent") {
            get("/students") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != UserRole.PARENT) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only parents can access this endpoint"))
                    return@get
                }
                val students = parentService.listParentStudents(principal.id)
                call.respond(students)
            }

            get("/assignments") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != UserRole.PARENT) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only parents can access this endpoint"))
                    return@get
                }

                val studentId = call.request.queryParameters["studentId"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("studentId is required"))
                    return@get
                }

                try {
                    val tasks = parentService.listParentAssignments(principal.id, studentId)
                    call.respond(tasks)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Student link not found"))
                }
            }

            get("/submissions") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role != UserRole.PARENT) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Only parents can access this endpoint"))
                    return@get
                }

                val studentId = call.request.queryParameters["studentId"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("studentId is required"))
                    return@get
                }

                try {
                    val submissions = parentService.listParentSubmissions(principal.id, studentId)
                    call.respond(submissions)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Student link not found"))
                }
            }
        }
    }
}

private fun UserRole.isTeacherRole(): Boolean = this == UserRole.TEACHER || this == UserRole.SCHOOL_ADMIN

private fun UserInfo.toDto() = UserDto(
    id = id.toString(),
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = role.name,
    schoolId = schoolId?.toString()
)
