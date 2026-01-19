package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.data.CreateTaskRequest
import de.aarondietz.lehrerlog.data.CreateTaskSubmissionRequest
import de.aarondietz.lehrerlog.services.TaskService
import de.aarondietz.lehrerlog.services.TaskSubmissionService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.util.UUID

fun Route.taskRoute(
    taskService: TaskService = TaskService(),
    submissionService: TaskSubmissionService = TaskSubmissionService()
) {
    authenticate("jwt") {
        route("/api/tasks") {
            get {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val classIdParam = call.request.queryParameters["classId"]
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("classId is required"))
                        return@get
                    }

                val classId = try {
                    UUID.fromString(classIdParam)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid classId"))
                    return@get
                }

                val tasks = taskService.getTasksByClass(schoolId, classId)
                call.respond(tasks)
            }

            post {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val request = call.receive<CreateTaskRequest>()
                if (request.title.isBlank() || request.dueAt.isBlank()) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Title and due date are required"))
                    return@post
                }

                val classId = try {
                    UUID.fromString(request.schoolClassId)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid classId"))
                    return@post
                }

                try {
                    val task = taskService.createTask(
                        schoolId = schoolId,
                        classId = classId,
                        title = request.title.trim(),
                        description = request.description?.trim()?.ifBlank { null },
                        dueAt = request.dueAt.trim(),
                        userId = principal.id
                    )
                    call.respond(HttpStatusCode.Created, task)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                }
            }

            get("/{id}/summary") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val taskId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                    return@get
                }

                try {
                    val summary = submissionService.getSummary(taskId, schoolId)
                    call.respond(summary)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Task not found"))
                }
            }

            get("/{id}/submissions") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val taskId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                    return@get
                }

                val submissions = submissionService.listSubmissions(taskId, schoolId)
                call.respond(submissions)
            }

            post("/{id}/submissions") {
                val principal = call.principal<UserPrincipal>()!!
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val taskId = call.parameters["id"]?.let {
                    try { UUID.fromString(it) } catch (e: Exception) { null }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                    return@post
                }

                val request = call.receive<CreateTaskSubmissionRequest>()
                val studentId = try {
                    UUID.fromString(request.studentId)
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid studentId"))
                    return@post
                }

                try {
                    val submission = submissionService.createSubmission(taskId, studentId, schoolId)
                    call.respond(HttpStatusCode.Created, submission)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                }
            }
        }
    }
}
