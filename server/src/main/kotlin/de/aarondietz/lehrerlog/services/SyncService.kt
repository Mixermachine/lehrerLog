package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.db.tables.*
import de.aarondietz.lehrerlog.sync.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

/**
 * Service for handling synchronization operations between client and server.
 */
class SyncService {

    private val studentService = StudentService()
    private val schoolClassService = SchoolClassService()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val MAX_CHANGES_PER_REQUEST = 100
    }

    /**
     * Get changes from the sync log since a specific log ID.
     * Returns changes for the specified school, ordered by sync log ID.
     */
    fun getChangesSince(schoolId: UUID, sinceLogId: Long): SyncChangesResponse {
        return transaction {
            val changes = SyncLog.selectAll()
                .where {
                    (SyncLog.schoolId eq schoolId) and
                    (SyncLog.id greater sinceLogId)
                }
                .orderBy(SyncLog.id to SortOrder.ASC)
                .limit(MAX_CHANGES_PER_REQUEST)
                .map { row ->
                    val entityType = row[SyncLog.entityType]
                    val entityId = row[SyncLog.entityId]
                    val operation = row[SyncLog.operation]

                    // Fetch entity data for CREATE and UPDATE operations
                    val data = if (operation != SyncOperation.DELETE) {
                        fetchEntityData(entityType, entityId, schoolId)
                    } else {
                        null
                    }

                    SyncChangeDto(
                        id = row[SyncLog.id].value,
                        entityType = entityType,
                        entityId = entityId.toString(),
                        operation = operation.name,
                        timestamp = row[SyncLog.createdAt].toEpochSecond(),
                        data = data
                    )
                }

            val lastSyncId = changes.lastOrNull()?.id ?: sinceLogId
            val hasMore = changes.size == MAX_CHANGES_PER_REQUEST

            SyncChangesResponse(
                changes = changes,
                lastSyncId = lastSyncId,
                hasMore = hasMore
            )
        }
    }

    /**
     * Push changes from client to server.
     * Processes each change and returns results.
     */
    fun pushChanges(schoolId: UUID, userId: UUID, request: PushChangesRequest): PushChangesResponse {
        val results = mutableListOf<PushChangeResult>()

        for (change in request.changes) {
            val result = processChange(schoolId, userId, change)
            results.add(result)
        }

        val successCount = results.count { it.success }
        val failureCount = results.count { !it.success }

        return PushChangesResponse(
            results = results,
            successCount = successCount,
            failureCount = failureCount
        )
    }

    private fun processChange(schoolId: UUID, userId: UUID, change: PushChangeRequest): PushChangeResult {
        return try {
            when (change.entityType) {
                EntityType.STUDENT.name -> processStudentChange(schoolId, userId, change)
                EntityType.SCHOOL_CLASS.name -> processSchoolClassChange(schoolId, userId, change)
                else -> PushChangeResult(
                    entityId = change.entityId,
                    success = false,
                    errorMessage = "Unknown entity type: ${change.entityType}"
                )
            }
        } catch (e: Exception) {
            PushChangeResult(
                entityId = change.entityId,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    private fun processStudentChange(schoolId: UUID, userId: UUID, change: PushChangeRequest): PushChangeResult {
        val entityId = UUID.fromString(change.entityId)

        return when (change.operation) {
            "CREATE" -> {
                val data = change.data
                if (data == null) {
                    return PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Missing data for CREATE operation"
                    )
                }

                val studentDto = json.decodeFromString<StudentDto>(data)
                val created = studentService.createStudent(
                    studentId = entityId,
                    schoolId = schoolId,
                    firstName = studentDto.firstName,
                    lastName = studentDto.lastName,
                    classIds = studentDto.classIds,
                    userId = userId
                )

                PushChangeResult(
                    entityId = created.id,
                    success = true
                )
            }

            "UPDATE" -> {
                val data = change.data
                if (data == null) {
                    return PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Missing data for UPDATE operation"
                    )
                }

                val studentDto = json.decodeFromString<StudentDto>(data)
                val result = studentService.updateStudent(
                    studentId = entityId,
                    schoolId = schoolId,
                    firstName = studentDto.firstName,
                    lastName = studentDto.lastName,
                    classIds = studentDto.classIds,
                    version = change.version,
                    userId = userId
                )

                when (result) {
                    is UpdateResult.Success -> PushChangeResult(
                        entityId = change.entityId,
                        success = true
                    )
                    is UpdateResult.NotFound -> PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Student not found"
                    )
                    is UpdateResult.VersionConflict -> PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Version conflict",
                        conflict = true
                    )
                }
            }

            "DELETE" -> {
                val deleted = studentService.deleteStudent(
                    studentId = entityId,
                    schoolId = schoolId,
                    userId = userId
                )

                PushChangeResult(
                    entityId = change.entityId,
                    success = deleted
                )
            }

            else -> PushChangeResult(
                entityId = change.entityId,
                success = false,
                errorMessage = "Unknown operation: ${change.operation}"
            )
        }
    }

    private fun processSchoolClassChange(schoolId: UUID, userId: UUID, change: PushChangeRequest): PushChangeResult {
        val entityId = UUID.fromString(change.entityId)

        return when (change.operation) {
            "CREATE" -> {
                val data = change.data
                if (data == null) {
                    return PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Missing data for CREATE operation"
                    )
                }

                val classDto = json.decodeFromString<SchoolClassDto>(data)
                val created = schoolClassService.createClass(
                    classId = entityId,
                    schoolId = schoolId,
                    name = classDto.name,
                    alternativeName = classDto.alternativeName,
                    userId = userId
                )

                PushChangeResult(
                    entityId = created.id,
                    success = true
                )
            }

            "UPDATE" -> {
                val data = change.data
                if (data == null) {
                    return PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Missing data for UPDATE operation"
                    )
                }

                val classDto = json.decodeFromString<SchoolClassDto>(data)
                val result = schoolClassService.updateClass(
                    classId = entityId,
                    schoolId = schoolId,
                    name = classDto.name,
                    alternativeName = classDto.alternativeName,
                    version = change.version,
                    userId = userId
                )

                when (result) {
                    is ClassUpdateResult.Success -> PushChangeResult(
                        entityId = change.entityId,
                        success = true
                    )
                    is ClassUpdateResult.NotFound -> PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Class not found"
                    )
                    is ClassUpdateResult.VersionConflict -> PushChangeResult(
                        entityId = change.entityId,
                        success = false,
                        errorMessage = "Version conflict",
                        conflict = true
                    )
                }
            }

            "DELETE" -> {
                val deleted = schoolClassService.deleteClass(
                    classId = entityId,
                    schoolId = schoolId,
                    userId = userId
                )

                PushChangeResult(
                    entityId = change.entityId,
                    success = deleted
                )
            }

            else -> PushChangeResult(
                entityId = change.entityId,
                success = false,
                errorMessage = "Unknown operation: ${change.operation}"
            )
        }
    }

    private fun fetchEntityData(entityType: String, entityId: UUID, schoolId: UUID): String? {
        return when (entityType) {
            EntityType.STUDENT.name -> {
                val student = studentService.getStudent(entityId, schoolId)
                student?.let { json.encodeToString(it) }
            }
            EntityType.SCHOOL_CLASS.name -> {
                val schoolClass = schoolClassService.getClass(entityId, schoolId)
                schoolClass?.let { json.encodeToString(it) }
            }
            else -> null
        }
    }
}
