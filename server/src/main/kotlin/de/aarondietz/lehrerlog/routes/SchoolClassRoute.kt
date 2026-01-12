package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.data.CreateSchoolClassRequest
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.UpdateSchoolClassRequest
import de.aarondietz.lehrerlog.db.tables.SchoolClasses
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

fun Route.schoolClassRoute() {
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

                val classes = transaction {
                    SchoolClasses.selectAll()
                        .where { SchoolClasses.schoolId eq schoolId }
                        .map { row ->
                            SchoolClassDto(
                                id = row[SchoolClasses.id].value.toString(),
                                schoolId = row[SchoolClasses.schoolId].value.toString(),
                                name = row[SchoolClasses.name],
                                alternativeName = row[SchoolClasses.alternativeName],
                                studentCount = 0, // TODO: Count from student_classes junction table
                                version = row[SchoolClasses.version],
                                createdAt = row[SchoolClasses.createdAt].toString(),
                                updatedAt = row[SchoolClasses.updatedAt].toString()
                            )
                        }
                }

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

                val schoolClass = transaction {
                    SchoolClasses.selectAll()
                        .where { (SchoolClasses.id eq classId) and (SchoolClasses.schoolId eq schoolId) }
                        .firstOrNull()
                        ?.let { row ->
                            SchoolClassDto(
                                id = row[SchoolClasses.id].value.toString(),
                                schoolId = row[SchoolClasses.schoolId].value.toString(),
                                name = row[SchoolClasses.name],
                                alternativeName = row[SchoolClasses.alternativeName],
                                studentCount = 0, // TODO: Count from student_classes junction table
                                version = row[SchoolClasses.version],
                                createdAt = row[SchoolClasses.createdAt].toString(),
                                updatedAt = row[SchoolClasses.updatedAt].toString()
                            )
                        }
                }

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

                val classId = transaction {
                    SchoolClasses.insertAndGetId {
                        it[SchoolClasses.schoolId] = schoolId
                        it[SchoolClasses.name] = request.name
                        it[SchoolClasses.alternativeName] = request.alternativeName
                        it[SchoolClasses.createdBy] = principal.id
                    }.value
                }

                val schoolClass = transaction {
                    SchoolClasses.selectAll()
                        .where { SchoolClasses.id eq classId }
                        .first()
                        .let { row ->
                            SchoolClassDto(
                                id = row[SchoolClasses.id].value.toString(),
                                schoolId = row[SchoolClasses.schoolId].value.toString(),
                                name = row[SchoolClasses.name],
                                alternativeName = row[SchoolClasses.alternativeName],
                                studentCount = 0,
                                version = row[SchoolClasses.version],
                                createdAt = row[SchoolClasses.createdAt].toString(),
                                updatedAt = row[SchoolClasses.updatedAt].toString()
                            )
                        }
                }

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

                val updated = transaction {
                    // Check if class exists and belongs to user's school
                    val existing = SchoolClasses.selectAll()
                        .where { (SchoolClasses.id eq classId) and (SchoolClasses.schoolId eq schoolId) }
                        .firstOrNull() ?: return@transaction null

                    // Optimistic locking check
                    if (existing[SchoolClasses.version] != request.version) {
                        return@transaction "version_conflict"
                    }

                    SchoolClasses.update({ SchoolClasses.id eq classId }) {
                        it[SchoolClasses.name] = request.name
                        it[SchoolClasses.alternativeName] = request.alternativeName
                        it[SchoolClasses.version] = request.version + 1
                        it[SchoolClasses.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                    }

                    SchoolClasses.selectAll()
                        .where { SchoolClasses.id eq classId }
                        .first()
                        .let { row ->
                            SchoolClassDto(
                                id = row[SchoolClasses.id].value.toString(),
                                schoolId = row[SchoolClasses.schoolId].value.toString(),
                                name = row[SchoolClasses.name],
                                alternativeName = row[SchoolClasses.alternativeName],
                                studentCount = 0,
                                version = row[SchoolClasses.version],
                                createdAt = row[SchoolClasses.createdAt].toString(),
                                updatedAt = row[SchoolClasses.updatedAt].toString()
                            )
                        }
                }

                when (updated) {
                    null -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Class not found"))
                    "version_conflict" -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Version conflict. Class was modified by another user."))
                    else -> call.respond(updated as SchoolClassDto)
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

                val deleted = transaction {
                    val count = SchoolClasses.deleteWhere {
                        (SchoolClasses.id eq classId) and (SchoolClasses.schoolId eq schoolId)
                    }
                    count > 0
                }

                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Class not found"))
                }
            }
        }
    }
}