package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.CreateTaskRequest
import de.aarondietz.lehrerlog.data.CreateTaskSubmissionRequest
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto
import de.aarondietz.lehrerlog.data.TaskTargetsRequest
import de.aarondietz.lehrerlog.data.UpdateTaskRequest
import de.aarondietz.lehrerlog.data.UpdateTaskSubmissionRequest
import de.aarondietz.lehrerlog.data.FileMetadataDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

class TaskRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    suspend fun getTasks(classId: String): Result<List<TaskDto>> {
        return try {
            val tasks = httpClient.get("$baseUrl/api/tasks") {
                parameter("classId", classId)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<TaskDto>>()
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTasksByStudent(studentId: String): Result<List<TaskDto>> {
        return try {
            val tasks = httpClient.get("$baseUrl/api/tasks") {
                parameter("studentId", studentId)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<TaskDto>>()
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
                setBody(request)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<TaskSubmissionDto>()
            Result.success(submission)
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

    suspend fun getSubmissions(taskId: String): Result<List<TaskSubmissionDto>> {
        return try {
            val submissions = httpClient.get("$baseUrl/api/tasks/$taskId/submissions") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<TaskSubmissionDto>>()
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
