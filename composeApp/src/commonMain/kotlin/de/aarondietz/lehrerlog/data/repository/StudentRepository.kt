package de.aarondietz.lehrerlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.lehrerLog
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import de.aarondietz.lehrerlog.currentTimeMillis

/**
 * Repository for Student data with offline-first capabilities.
 * Handles API calls and local database synchronization.
 */
class StudentRepository(
    private val httpClient: HttpClient,
    private val database: lehrerLog,
    private val baseUrl: String,
    private val logger: Logger
) {

    /**
     * Get all students for the current school from local database.
     * Returns a Flow that emits updates when data changes.
     */
    fun getStudentsFlow(schoolId: String): Flow<List<StudentDto>> {
        return database.studentQueries
            .getStudentsBySchool(schoolId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { students ->
                students.map { student ->
                    StudentDto(
                        id = student.id,
                        schoolId = student.schoolId,
                        firstName = student.firstName,
                        lastName = student.lastName,
                        classIds = emptyList(), // TODO: Implement class associations
                        version = student.version,
                        createdAt = student.createdAt.toString(),
                        updatedAt = student.updatedAt.toString()
                    )
                }
            }
    }

    /**
     * Load students from server and update local database.
     */
    suspend fun refreshStudents(schoolId: String): Result<List<StudentDto>> {
        return try {
            val students = httpClient.get("$baseUrl/api/students") {
                parameter("schoolId", schoolId)
            }.body<List<StudentDto>>()

            // Update local database
            students.forEach { student ->
                database.studentQueries.insertStudent(
                    id = student.id,
                    schoolId = student.schoolId,
                    firstName = student.firstName,
                    lastName = student.lastName,
                    version = student.version,
                    isSynced = 1, // Synced from server
                    localUpdatedAt = currentTimeMillis(),
                    createdAt = student.createdAt.toLongOrNull() ?: currentTimeMillis(),
                    updatedAt = student.updatedAt.toLongOrNull() ?: currentTimeMillis()
                )
            }

            Result.success(students)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new student (offline-first).
     * Saves to local DB immediately and queues for sync.
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createStudent(
        schoolId: String,
        firstName: String,
        lastName: String
    ): Result<StudentDto> {
        return try {
            val studentId = Uuid.random().toString()
            val now = currentTimeMillis()

            // Save to local database first
            database.studentQueries.insertStudent(
                id = studentId,
                schoolId = schoolId,
                firstName = firstName,
                lastName = lastName,
                version = 1L,
                isSynced = 0, // Not yet synced
                localUpdatedAt = now,
                createdAt = now,
                updatedAt = now
            )

            // Queue for sync
            database.pendingSyncQueries.insertPendingSync(
                entityType = "STUDENT",
                entityId = studentId,
                operation = "CREATE",
                createdAt = now
            )

            val student = StudentDto(
                id = studentId,
                schoolId = schoolId,
                firstName = firstName,
                lastName = lastName,
                classIds = emptyList(),
                version = 1L,
                createdAt = now.toString(),
                updatedAt = now.toString()
            )

            Result.success(student)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a student (offline-first).
     */
    suspend fun deleteStudent(studentId: String): Result<Unit> {
        return try {
            // Delete from local database
            database.studentQueries.deleteStudent(studentId)

            // Queue for sync
            database.pendingSyncQueries.insertPendingSync(
                entityType = "STUDENT",
                entityId = studentId,
                operation = "DELETE",
                createdAt = currentTimeMillis()
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
