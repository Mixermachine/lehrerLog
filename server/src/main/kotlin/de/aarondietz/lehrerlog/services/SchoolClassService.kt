package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.SchoolClassDto
import de.aarondietz.lehrerlog.db.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class SchoolClassService {

    /**
     * Get all classes for a specific school
     */
    fun getClassesBySchool(schoolId: UUID): List<SchoolClassDto> {
        return transaction {
            SchoolClasses.selectAll()
                .where { (SchoolClasses.schoolId eq schoolId) and SchoolClasses.archivedAt.isNull() }
                .map { it.toSchoolClassDto() }
        }
    }

    /**
     * Get a specific class by ID (with school validation)
     */
    fun getClass(classId: UUID, schoolId: UUID): SchoolClassDto? {
        return transaction {
            SchoolClasses.selectAll()
                .where {
                    (SchoolClasses.id eq classId) and
                        (SchoolClasses.schoolId eq schoolId) and
                        SchoolClasses.archivedAt.isNull()
                }
                .firstOrNull()
                ?.toSchoolClassDto()
        }
    }

    /**
     * Create a new class with client-specified ID (for sync) and log to sync
     */
    fun createClass(
        classId: UUID,
        schoolId: UUID,
        name: String,
        alternativeName: String?,
        userId: UUID
    ): SchoolClassDto {
        return transaction {
            SchoolClasses.insert {
                it[SchoolClasses.id] = classId
                it[SchoolClasses.schoolId] = schoolId
                it[SchoolClasses.name] = name
                it[SchoolClasses.alternativeName] = alternativeName
                it[SchoolClasses.createdBy] = userId
            }

            // Log the creation to sync log
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.SCHOOL_CLASS.name
                it[SyncLog.entityId] = classId
                it[SyncLog.operation] = SyncOperation.CREATE
                it[SyncLog.userId] = userId
            }

            getClass(classId, schoolId)!!
        }
    }

    /**
     * Create a new class with auto-generated ID and log to sync
     */
    fun createClass(
        schoolId: UUID,
        name: String,
        alternativeName: String?,
        userId: UUID
    ): SchoolClassDto {
        return transaction {
            val classId = SchoolClasses.insertAndGetId {
                it[SchoolClasses.schoolId] = schoolId
                it[SchoolClasses.name] = name
                it[SchoolClasses.alternativeName] = alternativeName
                it[SchoolClasses.createdBy] = userId
            }.value

            // Log the creation to sync log
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.SCHOOL_CLASS.name
                it[SyncLog.entityId] = classId
                it[SyncLog.operation] = SyncOperation.CREATE
                it[SyncLog.userId] = userId
            }

            SchoolClasses.selectAll()
                .where { SchoolClasses.id eq classId }
                .first()
                .toSchoolClassDto()
        }
    }

    /**
     * Update an existing class with optimistic locking
     */
    fun updateClass(
        classId: UUID,
        schoolId: UUID,
        name: String,
        alternativeName: String?,
        version: Long,
        userId: UUID
    ): ClassUpdateResult {
        return transaction {
            // Check if class exists and belongs to school
            val existing = SchoolClasses.selectAll()
                .where {
                    (SchoolClasses.id eq classId) and
                        (SchoolClasses.schoolId eq schoolId) and
                        SchoolClasses.archivedAt.isNull()
                }
                .firstOrNull() ?: return@transaction ClassUpdateResult.NotFound

            // Optimistic locking check
            if (existing[SchoolClasses.version] != version) {
                return@transaction ClassUpdateResult.VersionConflict
            }

            // Update the class
            SchoolClasses.update({ SchoolClasses.id eq classId }) {
                it[SchoolClasses.name] = name
                it[SchoolClasses.alternativeName] = alternativeName
                it[SchoolClasses.version] = version + 1
                it[SchoolClasses.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }

            // Log the update to sync log
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.SCHOOL_CLASS.name
                it[SyncLog.entityId] = classId
                it[SyncLog.operation] = SyncOperation.UPDATE
                it[SyncLog.userId] = userId
            }

            val updated = SchoolClasses.selectAll()
                .where { SchoolClasses.id eq classId }
                .first()
                .toSchoolClassDto()

            ClassUpdateResult.Success(updated)
        }
    }

    /**
     * Delete a class
     */
    fun deleteClass(classId: UUID, schoolId: UUID, userId: UUID): Boolean {
        return transaction {
            // Check if class exists and belongs to school
            val existing = SchoolClasses.selectAll()
                .where {
                    (SchoolClasses.id eq classId) and
                        (SchoolClasses.schoolId eq schoolId) and
                        SchoolClasses.archivedAt.isNull()
                }
                .firstOrNull() ?: return@transaction false

            // Log the deletion to sync log BEFORE deleting
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.SCHOOL_CLASS.name
                it[SyncLog.entityId] = classId
                it[SyncLog.operation] = SyncOperation.DELETE
                it[SyncLog.userId] = userId
            }

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val count = SchoolClasses.update({ SchoolClasses.id eq classId }) {
                it[SchoolClasses.archivedAt] = now
                it[SchoolClasses.updatedAt] = now
                it[SchoolClasses.version] = existing[SchoolClasses.version] + 1
            }

            count > 0
        }
    }

    private fun ResultRow.toSchoolClassDto() = SchoolClassDto(
        id = this[SchoolClasses.id].value.toString(),
        schoolId = this[SchoolClasses.schoolId].value.toString(),
        name = this[SchoolClasses.name],
        alternativeName = this[SchoolClasses.alternativeName],
        studentCount = 0, // TODO: Count from student_classes junction table
        version = this[SchoolClasses.version],
        createdAt = this[SchoolClasses.createdAt].toString(),
        updatedAt = this[SchoolClasses.updatedAt].toString()
    )
}

sealed class ClassUpdateResult {
    data class Success(val data: SchoolClassDto) : ClassUpdateResult()
    object NotFound : ClassUpdateResult()
    object VersionConflict : ClassUpdateResult()
}
