package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.data.CreateStudentRequest
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.UpdateStudentRequest
import de.aarondietz.lehrerlog.db.tables.Students
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

fun Route.studentRoute() {
    authenticate("jwt") {
        route("/api/students") {

            // List all students for the user's school
            get {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val students = transaction {
                    Students.selectAll()
                        .where { Students.schoolId eq schoolId }
                        .map { row ->
                            StudentDto(
                                id = row[Students.id].value.toString(),
                                schoolId = row[Students.schoolId].value.toString(),
                                firstName = row[Students.firstName],
                                lastName = row[Students.lastName],
                                classIds = emptyList(), // TODO: Load from student_classes junction table
                                version = row[Students.version],
                                createdAt = row[Students.createdAt].toString(),
                                updatedAt = row[Students.updatedAt].toString()
                            )
                        }
                }

                call.respond(students)
            }

            // Get a specific student
            get("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val studentId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid student ID"))
                    return@get
                }

                val student = transaction {
                    Students.selectAll()
                        .where { (Students.id eq studentId) and (Students.schoolId eq schoolId) }
                        .firstOrNull()
                        ?.let { row ->
                            StudentDto(
                                id = row[Students.id].value.toString(),
                                schoolId = row[Students.schoolId].value.toString(),
                                firstName = row[Students.firstName],
                                lastName = row[Students.lastName],
                                classIds = emptyList(), // TODO: Load from student_classes junction table
                                version = row[Students.version],
                                createdAt = row[Students.createdAt].toString(),
                                updatedAt = row[Students.updatedAt].toString()
                            )
                        }
                }

                if (student == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Student not found"))
                } else {
                    call.respond(student)
                }
            }

            // Create a new student
            post {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val request = call.receive<CreateStudentRequest>()

                if (request.firstName.isBlank() || request.lastName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("First name and last name are required"))
                    return@post
                }

                val studentId = transaction {
                    Students.insertAndGetId {
                        it[Students.schoolId] = schoolId
                        it[Students.firstName] = request.firstName
                        it[Students.lastName] = request.lastName
                        it[Students.createdBy] = principal.id
                    }.value
                }

                val student = transaction {
                    Students.selectAll()
                        .where { Students.id eq studentId }
                        .first()
                        .let { row ->
                            StudentDto(
                                id = row[Students.id].value.toString(),
                                schoolId = row[Students.schoolId].value.toString(),
                                firstName = row[Students.firstName],
                                lastName = row[Students.lastName],
                                classIds = emptyList(),
                                version = row[Students.version],
                                createdAt = row[Students.createdAt].toString(),
                                updatedAt = row[Students.updatedAt].toString()
                            )
                        }
                }

                call.respond(HttpStatusCode.Created, student)
            }

            // Update a student
            put("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@put
                }

                val studentId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid student ID"))
                    return@put
                }

                val request = call.receive<UpdateStudentRequest>()

                if (request.firstName.isBlank() || request.lastName.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("First name and last name are required"))
                    return@put
                }

                val updated = transaction {
                    // Check if student exists and belongs to user's school
                    val existing = Students.selectAll()
                        .where { (Students.id eq studentId) and (Students.schoolId eq schoolId) }
                        .firstOrNull() ?: return@transaction null

                    // Optimistic locking check
                    if (existing[Students.version] != request.version) {
                        return@transaction "version_conflict"
                    }

                    Students.update({ Students.id eq studentId }) {
                        it[Students.firstName] = request.firstName
                        it[Students.lastName] = request.lastName
                        it[Students.version] = request.version + 1
                        it[Students.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
                    }

                    Students.selectAll()
                        .where { Students.id eq studentId }
                        .first()
                        .let { row ->
                            StudentDto(
                                id = row[Students.id].value.toString(),
                                schoolId = row[Students.schoolId].value.toString(),
                                firstName = row[Students.firstName],
                                lastName = row[Students.lastName],
                                classIds = emptyList(),
                                version = row[Students.version],
                                createdAt = row[Students.createdAt].toString(),
                                updatedAt = row[Students.updatedAt].toString()
                            )
                        }
                }

                when (updated) {
                    null -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Student not found"))
                    "version_conflict" -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Version conflict. Student was modified by another user."))
                    else -> call.respond(updated as StudentDto)
                }
            }

            // Delete a student
            delete("/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId

                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@delete
                }

                val studentId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid student ID"))
                    return@delete
                }

                val deleted = transaction {
                    val count = Students.deleteWhere {
                        (Students.id eq studentId) and (Students.schoolId eq schoolId)
                    }
                    count > 0
                }

                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Student not found"))
                }
            }
        }
    }
}