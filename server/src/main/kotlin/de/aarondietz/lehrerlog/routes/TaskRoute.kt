package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.services.TaskService
import de.aarondietz.lehrerlog.services.TaskSubmissionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Route.taskRoute(
    taskService: TaskService = TaskService(),
    submissionService: TaskSubmissionService = TaskSubmissionService()
) {
    authenticate("jwt") {
        route("/api/tasks") {
            get {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val classIdParam = call.request.queryParameters["classId"]
                val studentIdParam = call.request.queryParameters["studentId"]
                if (classIdParam == null && studentIdParam == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("classId or studentId is required"))
                    return@get
                }

                if (classIdParam != null) {
                    val classId = try {
                        UUID.fromString(classIdParam)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid classId"))
                        return@get
                    }

                    val tasks = taskService.getTasksByClass(schoolId, classId)
                    call.respond(tasks)
                } else {
                    val studentId = studentIdParam?.let {
                        try {
                            UUID.fromString(it)
                        } catch (e: Exception) {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid studentId"))
                            return@get
                        }
                    } ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Student ID is required"))
                        return@get
                    }

                    val tasks = taskService.getTasksByStudent(schoolId, studentId)
                    call.respond(tasks)
                }
            }

            post {
                val principal = call.getPrincipalOrRespond()
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

            put("/{id}") {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@put
                }

                val taskId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                    return@put
                }

                val request = call.receive<UpdateTaskRequest>()
                if (request.title?.isBlank() == true || request.dueAt?.isBlank() == true) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Title and due date cannot be blank"))
                    return@put
                }

                try {
                    val task = taskService.updateTask(
                        taskId = taskId,
                        schoolId = schoolId,
                        title = request.title,
                        description = request.description,
                        dueAt = request.dueAt
                    )
                    call.respond(task)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Task not found"))
                }
            }

            delete("/{id}") {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@delete
                }

                val taskId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                    return@delete
                }

                try {
                    taskService.deleteTask(taskId, schoolId)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Task not found"))
                }
            }

            post("/{id}/targets") {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val taskId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                    return@post
                }

                val request = call.receive<TaskTargetsRequest>()
                val addIds = request.addStudentIds.mapNotNull {
                    runCatching { UUID.fromString(it) }.getOrNull()
                }
                val removeIds = request.removeStudentIds.mapNotNull {
                    runCatching { UUID.fromString(it) }.getOrNull()
                }
                if (addIds.size != request.addStudentIds.size || removeIds.size != request.removeStudentIds.size) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid studentId"))
                    return@post
                }

                try {
                    taskService.updateTargets(taskId, schoolId, addIds, removeIds)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                }
            }

            get("/{id}/summary") {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val taskId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
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
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@get
                }

                val taskId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                    return@get
                }

                val submissions = submissionService.listSubmissions(taskId, schoolId)
                call.respond(submissions)
            }

            post("/{id}/submissions") {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val taskId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
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
                    val submission = submissionService.createSubmission(
                        taskId = taskId,
                        studentId = studentId,
                        schoolId = schoolId,
                        submissionType = request.submissionType,
                        grade = request.grade,
                        note = request.note
                    )
                    call.respond(HttpStatusCode.Created, submission)
                } catch (e: TaskSubmissionService.DuplicateSubmissionException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Submission already exists"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                }
            }

            post("/{id}/submissions/in-person") {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val taskId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
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
                    val submission = submissionService.createSubmission(
                        taskId = taskId,
                        studentId = studentId,
                        schoolId = schoolId,
                        submissionType = TaskSubmissionType.IN_PERSON,
                        grade = request.grade,
                        note = request.note
                    )
                    call.respond(HttpStatusCode.Created, submission)
                } catch (e: TaskSubmissionService.DuplicateSubmissionException) {
                    call.respond(HttpStatusCode.Conflict, ErrorResponse(e.message ?: "Submission already exists"))
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                }
            }

        }

        route("/api/submissions") {
            patch("/{id}") {
                val principal = call.getPrincipalOrRespond()
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@patch
                }

                val submissionId = call.parameters["id"]?.let {
                    try {
                        UUID.fromString(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid submission ID"))
                    return@patch
                }

                val request = call.receive<UpdateTaskSubmissionRequest>()
                if (request.grade == null && request.note == null && request.lateStatus == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("grade, note, or lateStatus is required"))
                    return@patch
                }

                try {
                    val submission = submissionService.updateSubmission(
                        submissionId = submissionId,
                        schoolId = schoolId,
                        grade = request.grade,
                        note = request.note,
                        lateStatus = request.lateStatus,
                        decidedBy = principal.id
                    )
                    call.respond(submission)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse(e.message ?: "Submission not found"))
                }
            }
        }
    }
}
