package de.aarondietz.lehrerlog.routes

import de.aarondietz.lehrerlog.auth.ErrorResponse
import de.aarondietz.lehrerlog.auth.UserPrincipal
import de.aarondietz.lehrerlog.db.tables.UserRole
import de.aarondietz.lehrerlog.db.tables.TaskSubmissions
import de.aarondietz.lehrerlog.services.FileStorageService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import java.util.UUID

fun Route.fileRoute(
    fileStorageService: FileStorageService = FileStorageService()
) {
    authenticate("jwt") {
        route("/api") {
            post("/tasks/{id}/files") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role == UserRole.PARENT) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Parents cannot upload files"))
                    return@post
                }
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val taskId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                        return@post
                    }

                val file = call.receiveSingleFile()
                if (file == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("File is required"))
                    return@post
                }

                try {
                    val meta = fileStorageService.storeTaskFile(
                        taskId = taskId,
                        schoolId = schoolId,
                        userId = principal.id,
                        fileName = file.fileName,
                        contentType = file.contentType,
                        sizeBytes = file.bytes.size.toLong(),
                        input = file.bytes.inputStream()
                    )
                    call.respond(HttpStatusCode.Created, meta)
                } catch (e: IllegalArgumentException) {
                    when (e.message) {
                        "FILE_TOO_LARGE" -> call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("File exceeds size limit"))
                        "QUOTA_EXCEEDED" -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Storage quota exceeded"))
                        else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    }
                }
            }

            post("/tasks/{id}/submissions/{submissionId}/files") {
                val principal = call.principal<UserPrincipal>()!!
                if (principal.role == UserRole.PARENT) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("Parents cannot upload files"))
                    return@post
                }
                val schoolId = principal.schoolId
                if (schoolId == null) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                    return@post
                }

                val taskId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid task ID"))
                        return@post
                    }

                val submissionId = call.parameters["submissionId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid submission ID"))
                        return@post
                    }

                val belongsToTask = transaction {
                    TaskSubmissions.selectAll()
                        .where { (TaskSubmissions.id eq submissionId) and (TaskSubmissions.taskId eq taskId) }
                        .count() > 0
                }
                if (!belongsToTask) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("Submission not found"))
                    return@post
                }

                val file = call.receiveSingleFile()
                if (file == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("File is required"))
                    return@post
                }

                try {
                    val meta = fileStorageService.storeSubmissionFile(
                        submissionId = submissionId,
                        schoolId = schoolId,
                        userId = principal.id,
                        fileName = file.fileName,
                        contentType = file.contentType,
                        sizeBytes = file.bytes.size.toLong(),
                        input = file.bytes.inputStream()
                    )
                    call.respond(HttpStatusCode.Created, meta)
                } catch (e: IllegalArgumentException) {
                    when (e.message) {
                        "FILE_TOO_LARGE" -> call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("File exceeds size limit"))
                        "QUOTA_EXCEEDED" -> call.respond(HttpStatusCode.Conflict, ErrorResponse("Storage quota exceeded"))
                        else -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(e.message ?: "Invalid request"))
                    }
                }
            }

            get("/files/{id}") {
                val principal = call.principal<UserPrincipal>()!!
                val fileId = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid file ID"))
                        return@get
                    }
                val resolved = if (principal.role == UserRole.PARENT) {
                    fileStorageService.resolveFileForParent(fileId, principal.id)
                } else {
                    val schoolId = principal.schoolId
                    if (schoolId == null) {
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("User not associated with a school"))
                        return@get
                    }
                    fileStorageService.resolveFile(fileId, schoolId)
                }

                if (resolved == null) {
                    call.respond(HttpStatusCode.NotFound, ErrorResponse("File not found"))
                    return@get
                }

                call.response.headers.append(HttpHeaders.ContentType, resolved.mimeType)
                call.respondFile(resolved.path.toFile())
            }
        }
    }
}

private data class IncomingFile(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray
)

private suspend fun ApplicationCall.receiveSingleFile(): IncomingFile? {
    val multipart = receiveMultipart()
    var found: IncomingFile? = null
    multipart.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                if (found == null) {
                    val bytes = part.provider().readRemaining().readBytes()
                    val fileName = part.originalFileName ?: "upload.bin"
                    val contentType = part.contentType?.toString() ?: "application/octet-stream"
                    found = IncomingFile(fileName, contentType, bytes)
                }
            }
            else -> Unit
        }
        part.dispose()
    }
    return found
}
