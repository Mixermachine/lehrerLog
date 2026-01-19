package de.aarondietz.lehrerlog.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import co.touchlab.kermit.Logger
import de.aarondietz.lehrerlog.auth.TokenStorage
import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.database.DatabaseManager
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
 * Repository for SchoolClass data with offline-first capabilities.
 */
class SchoolClassRepository(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val databaseManager: DatabaseManager,
    private val baseUrl: String,
    private val logger: Logger
) {
    private val database
        get() = databaseManager.db

    /**
     * Get all classes for the current school from local database.
     */
    fun getClassesFlow(schoolId: String): Flow<List<SchoolClassDto>> {
        return database.schoolClassQueries
            .getClassesBySchool(schoolId)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { classes ->
                classes.map { schoolClass ->
                    SchoolClassDto(
                        id = schoolClass.id,
                        schoolId = schoolClass.schoolId,
                        name = schoolClass.name,
                        alternativeName = schoolClass.alternativeName,
                        version = schoolClass.version,
                        createdAt = schoolClass.createdAt.toString(),
                        updatedAt = schoolClass.updatedAt.toString()
                    )
                }
            }
    }

    /**
     * Load classes from server and update local database.
     */
    suspend fun refreshClasses(schoolId: String): Result<List<SchoolClassDto>> {
        return try {
            val classes = httpClient.get("$baseUrl/api/classes") {
                parameter("schoolId", schoolId)
                tokenStorage.getAccessToken()?.let { header("Authorization", "Bearer $it") }
            }.body<List<SchoolClassDto>>()

            // Update local database
            classes.forEach { schoolClass ->
                database.schoolClassQueries.insertClass(
                    id = schoolClass.id,
                    schoolId = schoolClass.schoolId,
                    name = schoolClass.name,
                    alternativeName = schoolClass.alternativeName,
                    version = schoolClass.version,
                    isSynced = 1,
                    localUpdatedAt = currentTimeMillis(),
                    createdAt = schoolClass.createdAt.toLongOrNull() ?: currentTimeMillis(),
                    updatedAt = schoolClass.updatedAt.toLongOrNull() ?: currentTimeMillis()
                )
            }

            Result.success(classes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new class (offline-first).
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun createClass(
        schoolId: String,
        name: String,
        alternativeName: String? = null
    ): Result<SchoolClassDto> {
        logger.d { "createClass called: schoolId=$schoolId, name=$name, alternativeName=$alternativeName" }
        return try {
            val classId = Uuid.random().toString()
            val now = currentTimeMillis()

            logger.d { "Generated classId=$classId, inserting into local database" }

            // Save to local database first
            database.schoolClassQueries.insertClass(
                id = classId,
                schoolId = schoolId,
                name = name,
                alternativeName = alternativeName,
                version = 1L,
                isSynced = 0,
                localUpdatedAt = now,
                createdAt = now,
                updatedAt = now
            )

            logger.d { "Class saved to local database, queuing for sync" }

            // Queue for sync
            database.pendingSyncQueries.insertPendingSync(
                entityType = "SCHOOL_CLASS",
                entityId = classId,
                operation = "CREATE",
                createdAt = now
            )

            logger.i { "Class created successfully and queued for sync: classId=$classId" }

            val schoolClass = SchoolClassDto(
                id = classId,
                schoolId = schoolId,
                name = name,
                alternativeName = alternativeName,
                version = 1L,
                createdAt = now.toString(),
                updatedAt = now.toString()
            )

            Result.success(schoolClass)
        } catch (e: Exception) {
            logger.e(e) { "Failed to create class: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * Delete a class (offline-first).
     */
    suspend fun deleteClass(classId: String): Result<Unit> {
        return try {
            // Delete from local database
            database.schoolClassQueries.deleteClass(classId)

            // Queue for sync
            database.pendingSyncQueries.insertPendingSync(
                entityType = "SCHOOL_CLASS",
                entityId = classId,
                operation = "DELETE",
                createdAt = currentTimeMillis()
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
