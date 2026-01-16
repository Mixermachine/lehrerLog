package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.data.CreateStudentRequest
import de.aarondietz.lehrerlog.data.UpdateStudentRequest
import de.aarondietz.lehrerlog.services.StudentService
import de.aarondietz.lehrerlog.services.UpdateResult
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.studentRoute(studentService: StudentService = StudentService()) {
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

                val students = studentService.getStudentsBySchool(schoolId)
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

                val student = studentService.getStudent(studentId, schoolId)

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

                val student = studentService.createStudent(
                    schoolId = schoolId,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    userId = principal.id
                )

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

                when (val result = studentService.updateStudent(
                    studentId = studentId,
                    schoolId = schoolId,
                    firstName = request.firstName,
                    lastName = request.lastName,
                    version = request.version,
                    userId = principal.id
                )) {
                    is UpdateResult.Success -> call.respond(result.data)
                    is UpdateResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("Student not found"))
                    is UpdateResult.VersionConflict -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Version conflict. Student was modified by another user."))
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

                val deleted = studentService.deleteStudent(studentId, schoolId, principal.id)

                if (deleted) {
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Student not found"))
                }
            }
        }
    }
}
