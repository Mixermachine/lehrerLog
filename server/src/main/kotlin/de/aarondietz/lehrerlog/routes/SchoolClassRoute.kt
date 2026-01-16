package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.data.CreateSchoolClassRequest
import de.aarondietz.lehrerlog.data.UpdateSchoolClassRequest
import de.aarondietz.lehrerlog.services.ClassUpdateResult
import de.aarondietz.lehrerlog.services.SchoolClassService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.schoolClassRoute(schoolClassService: SchoolClassService = SchoolClassService()) {
    authenticate("jwt") {
        route("/api/classes") {

            // List all classes for the user's school
            get {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val classes = schoolClassService.getClassesBySchool(schoolId)
                call.respond(classes)
            }

            // Get a specific class
            get("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val classId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid class ID"))
                    return@get
                }

                val schoolClass = schoolClassService.getClass(classId, schoolId)

                if (schoolClass == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Class not found"))
                } else {
                    call.respond(schoolClass)
                }
            }

            // Create a new class
            post {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val request = call.receive<CreateSchoolClassRequest>()

                if (request.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Class name is required"))
                    return@post
                }

                val schoolClass = schoolClassService.createClass(
                    schoolId = schoolId,
                    name = request.name,
                    alternativeName = request.alternativeName,
                    userId = principal.id
                )

                call.respond(HttpStatusCode.Created, schoolClass)
            }

            // Update a class
            put("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@put
                }

                val classId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid class ID"))
                    return@put
                }

                val request = call.receive<UpdateSchoolClassRequest>()

                if (request.name.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Class name is required"))
                    return@put
                }

                when (val result = schoolClassService.updateClass(
                    classId = classId,
                    schoolId = schoolId,
                    name = request.name,
                    alternativeName = request.alternativeName,
                    version = request.version,
                    userId = principal.id
                )) {
                    is ClassUpdateResult.Success -> call.respond(result.data)
                    is ClassUpdateResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Class not found"))
                    is ClassUpdateResult.VersionConflict -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Version conflict. Class was modified by another user."))
                }
            }

            // Delete a class
            delete("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@delete
                }

                val classId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid class ID"))
                    return@delete
                }

                val deleted = schoolClassService.deleteClass(classId, schoolId, principal.id)

                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Class not found"))
                }
            }
        }
    }
}
