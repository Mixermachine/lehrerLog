package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.AuthRepository
import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.network.withTokenRefresh
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*

class TaskRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String,
    private val authRepository: AuthRepository? = null
) {
    suspend fun getTasks(classId: String): Result<List<TaskDto>> {
        return try {
            val tasks = withTokenRefresh(authRepository) {
                httpClient.get("$baseUrl/api/tasks") {
                    parameter("classId", classId)
                    tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                }.body<List<TaskDto>>()
            }
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTasksByStudent(studentId: String): Result<List<TaskDto>> {
        return try {
            val tasks = withTokenRefresh(authRepository) {
                httpClient.get("$baseUrl/api/tasks") {
                    parameter("studentId", studentId)
                    tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                }.body<List<TaskDto>>()
            }
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTask(
        classId: String,
        title: String,
        description: String?,
        dueAt: String
    ): Result<TaskDto> {
        return try {
            val task = httpClient.post("$baseUrl/api/tasks") {
                contentType(ContentType.Application.Json)
                setBody(CreateTaskRequest(classId, title, description, dueAt))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<TaskDto>()
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTask(
        taskId: String,
        title: String? = null,
        description: String? = null,
        dueAt: String? = null
    ): Result<TaskDto> {
        return try {
            val task = httpClient.put("$baseUrl/api/tasks/$taskId") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTaskRequest(title, description, dueAt))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<TaskDto>()
            Result.success(task)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTask(taskId: String): Result<Unit> {
        return try {
            httpClient.delete("$baseUrl/api/tasks/$taskId") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateTargets(
        taskId: String,
        addStudentIds: List<String>,
        removeStudentIds: List<String>
    ): Result<Unit> {
        return try {
            httpClient.post("$baseUrl/api/tasks/$taskId/targets") {
                contentType(ContentType.Application.Json)
                setBody(TaskTargetsRequest(addStudentIds, removeStudentIds))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createSubmission(
        taskId: String,
        request: CreateTaskSubmissionRequest
    ): Result<TaskSubmissionDto> {
        return try {
            val submission = httpClient.post("$baseUrl/api/tasks/$taskId/submissions") {
                contentType(ContentType.Application.Json)
                setBody(request)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<TaskSubmissionDto>()
            Result.success(submission)
        } catch (e: ClientRequestException) {
            val responseText = e.response.readBodyTextSafely()
            val isInPersonNotTargetedCase = e.response.status == HttpStatusCode.BadRequest &&
                request.submissionType == TaskSubmissionType.IN_PERSON
            if (
                isInPersonNotTargetedCase ||
                responseText.contains("not targeted", ignoreCase = true) ||
                e.message.contains("not targeted", ignoreCase = true)
            ) {
                Result.failure(TaskStudentNotTargetedException("Student not targeted for this task"))
            } else {
                val message = parseApiErrorMessage(responseText) ?: e.message
                Result.failure(IllegalStateException(message, e))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSubmission(
        submissionId: String,
        grade: Double? = null,
        note: String? = null
    ): Result<TaskSubmissionDto> {
        return try {
            val submission = httpClient.patch("$baseUrl/api/submissions/$submissionId") {
                contentType(ContentType.Application.Json)
                setBody(UpdateTaskSubmissionRequest(grade, note))
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<TaskSubmissionDto>()
            Result.success(submission)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSubmissionSummary(taskId: String): Result<TaskSubmissionSummaryDto> {
        return try {
            val summary = httpClient.get("$baseUrl/api/tasks/$taskId/summary") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }
                .body<TaskSubmissionSummaryDto>()
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTaskTargets(taskId: String): Result<TaskTargetsResponse> {
        return try {
            val targets = withTokenRefresh(authRepository) {
                httpClient.get("$baseUrl/api/tasks/$taskId/targets") {
                    tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                }.body<TaskTargetsResponse>()
            }
            Result.success(targets)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSubmissions(taskId: String): Result<List<TaskSubmissionDto>> {
        return try {
            val submissions = withTokenRefresh(authRepository) {
                httpClient.get("$baseUrl/api/tasks/$taskId/submissions") {
                    tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                }.body<List<TaskSubmissionDto>>()
            }
            Result.success(submissions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadTaskFile(taskId: String, file: UploadFilePayload): FileUploadResult {
        return uploadFile("$baseUrl/api/tasks/$taskId/files", file)
    }

    suspend fun uploadSubmissionFile(taskId: String, submissionId: String, file: UploadFilePayload): FileUploadResult {
        return uploadFile("$baseUrl/api/tasks/$taskId/submissions/$submissionId/files", file)
    }

    suspend fun getTaskFile(taskId: String): Result<FileMetadataDto?> {
        return try {
            val file = withTokenRefresh(authRepository) {
                httpClient.get("$baseUrl/api/tasks/$taskId/file") {
                    tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                }.body<FileMetadataDto>()
            }
            Result.success(file)
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                Result.success(null)
            } else {
                Result.failure(e)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun uploadFile(url: String, file: UploadFilePayload): FileUploadResult {
        return try {
            val response = httpClient.post(url) {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                "file",
                                file.bytes,
                                Headers.build {
                                    append(HttpHeaders.ContentType, file.contentType)
                                    append(HttpHeaders.ContentDisposition, "filename=\"${file.fileName}\"")
                                }
                            )
                        }
                    )
                )
            }.body<FileMetadataDto>()
            FileUploadResult.Success(response)
        } catch (e: ClientRequestException) {
            when (e.response.status) {
                HttpStatusCode.PayloadTooLarge -> FileUploadResult.FileTooLarge
                HttpStatusCode.Conflict -> FileUploadResult.QuotaExceeded
                else -> FileUploadResult.Error("Upload failed: ${e.response.status}")
            }
        } catch (e: Exception) {
            FileUploadResult.Error("Upload failed: ${e.message}")
        }
    }
}

private suspend fun HttpResponse.readBodyTextSafely(): String {
    return runCatching { bodyAsText() }.getOrDefault("")
}

private fun parseApiErrorMessage(rawText: String): String? {
    val text = rawText.trim()
    if (text.isBlank()) return null
    val errorMatch = Regex(""""error"\s*:\s*"([^"]+)"""").find(text)
    return errorMatch?.groupValues?.getOrNull(1) ?: text
}

class TaskStudentNotTargetedException(message: String) : IllegalStateException(message)

data class UploadFilePayload(
    val fileName: String,
    val bytes: ByteArray,
    val contentType: String
)

sealed class FileUploadResult {
    data class Success(val metadata: FileMetadataDto) : FileUploadResult()
    data class Error(val message: String) : FileUploadResult()
    object QuotaExceeded : FileUploadResult()
    object FileTooLarge : FileUploadResult()
}
