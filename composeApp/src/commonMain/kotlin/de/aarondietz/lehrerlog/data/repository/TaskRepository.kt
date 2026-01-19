package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.CreateTaskRequest
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

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
}
