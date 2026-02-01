package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ParentRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
) {
    private val studentsState = MutableStateFlow<List<StudentDto>>(emptyList())

    fun studentsFlow(): Flow<List<StudentDto>> = studentsState.asStateFlow()

    suspend fun refreshStudents(): Result<List<StudentDto>> {
        return try {
            val students = httpClient.get("$baseUrl/api/parent/students") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<StudentDto>>()

            studentsState.value = students
            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listAssignments(studentId: String): Result<List<TaskDto>> {
        return try {
            val tasks = httpClient.get("$baseUrl/api/parent/assignments") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                url { parameters.append("studentId", studentId) }
            }.body<List<TaskDto>>()
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listSubmissions(studentId: String): Result<List<TaskSubmissionDto>> {
        return try {
            val submissions = httpClient.get("$baseUrl/api/parent/submissions") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
                url { parameters.append("studentId", studentId) }
            }.body<List<TaskSubmissionDto>>()
            Result.success(submissions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
