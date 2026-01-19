package de.aarondietz.lehrerlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.currentTimeMillis
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.database.DatabaseManager
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Repository for Student data with offline-first capabilities.
 * Handles API calls and local database synchronization.
 */
class StudentRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val databaseManager: DatabaseManager,
    private val baseUrl: String,
    private val logger: Logger
) {
    private val database
        get() = databaseManager.db

    /**
     * Get all students for the current school from local database.
     * Returns a Flow that emits updates when data changes.
     */
    fun getStudentsFlow(schoolId: String): Flow<List<StudentDto>> {
        val now = currentTimeMillis()
        return database.studentClassQueries
            .getStudentsWithClassesBySchool(now, schoolId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.groupBy { it.id }.map { (_, groupedRows) ->
                    val student = groupedRows.first()
                    val classIds = groupedRows.mapNotNull { it.classId }.distinct()
                    StudentDto(
                        id = student.id,
                        schoolId = student.schoolId,
                        firstName = student.firstName,
                        lastName = student.lastName,
                        classIds = classIds,
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
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<StudentDto>>()

            // Update local database
            students.forEach { student ->
                val now = currentTimeMillis()
                database.studentQueries.insertStudent(
                    id = student.id,
                    schoolId = student.schoolId,
                    firstName = student.firstName,
                    lastName = student.lastName,
                    version = student.version,
                    isSynced = 1, // Synced from server
                    localUpdatedAt = now,
                    createdAt = student.createdAt.toLongOrNull() ?: now,
                    updatedAt = student.updatedAt.toLongOrNull() ?: now
                )
                database.studentClassQueries.deleteClassesForStudent(student.id)
                student.classIds.forEach { classId ->
                    database.studentClassQueries.insertStudentClass(
                        studentId = student.id,
                        classId = classId,
                        validFrom = now,
                        validTill = null,
                        version = 1L,
                        createdAt = now
                    )
                }
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
        classId: String?,
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

            if (!classId.isNullOrBlank()) {
                database.studentClassQueries.insertStudentClass(
                    studentId = studentId,
                    classId = classId,
                    validFrom = now,
                    validTill = null,
                    version = 1L,
                    createdAt = now
                )
            }

            val student = StudentDto(
                id = studentId,
                schoolId = schoolId,
                firstName = firstName,
                lastName = lastName,
                classIds = classId?.let { listOf(it) }.orEmpty(),
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
            database.studentClassQueries.deleteClassesForStudent(studentId)

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
