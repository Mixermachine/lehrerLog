package de.aarondietz.lehrerlog.data.repository

import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.StudentDto
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for Student data (online-first).
 */
class StudentRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val baseUrl: String
)  {
    private val studentsState = MutableStateFlow<List<StudentDto>>(emptyList())

    /**
     * Get students from in-memory state.
     */
    suspend fun getStudentsFlow(): Flow<List<StudentDto>> = studentsState.asStateFlow()

    /**
     * Load students from server and update in-memory state.
     */
    suspend fun refreshStudents(schoolId: String): Result<List<StudentDto>> {
        return try {
            val students = httpClient.get("$baseUrl/api/students") {
                parameter("schoolId", schoolId)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<StudentDto>>()

            studentsState.value = students

            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new student on the server.
     */
    suspend fun createStudent(
        classId: String?,
        firstName: String,
        lastName: String
    ): Result<StudentDto> {
        return try {
            val student = httpClient.post("$baseUrl/api/students") {
                setBody(
                    de.aarondietz.lehrerlog.data.CreateStudentRequest(
                        firstName = firstName,
                        lastName = lastName,
                        classIds = classId?.let { listOf(it) }.orEmpty()
                    )
                )
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<StudentDto>()

            studentsState.value = studentsState.value + student

            Result.success(student)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a student on the server.
     */
    suspend fun deleteStudent(studentId: String): Result<Unit> {
        return try {
            httpClient.delete("$baseUrl/api/students/$studentId") {
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }
            studentsState.value = studentsState.value.filterNot { it.id == studentId }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
