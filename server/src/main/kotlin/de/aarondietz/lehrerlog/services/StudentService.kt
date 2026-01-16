package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.StudentDto
import de.aarondietz.lehrerlog.db.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class StudentService {

    /**
     * Get all students for a specific school
     */
    fun getStudentsBySchool(schoolId: UUID): List<StudentDto> {
        return transaction {
            Students.selectAll()
                .where { Students.schoolId eq schoolId }
                .map { it.toStudentDto() }
        }
    }

    /**
     * Get a specific student by ID (with school validation)
     */
    fun getStudent(studentId: UUID, schoolId: UUID): StudentDto? {
        return transaction {
            Students.selectAll()
                .where { (Students.id eq studentId) and (Students.schoolId eq schoolId) }
                .firstOrNull()
                ?.toStudentDto()
        }
    }

    /**
     * Create a new student with client-specified ID (for sync) and log to sync
     */
    fun createStudent(
        studentId: UUID,
        schoolId: UUID,
        firstName: String,
        lastName: String,
        userId: UUID
    ): StudentDto {
        return transaction {
            Students.insert {
                it[Students.id] = studentId
                it[Students.schoolId] = schoolId
                it[Students.firstName] = firstName
                it[Students.lastName] = lastName
                it[Students.createdBy] = userId
            }

            // Log the creation to sync log
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.STUDENT.name
                it[SyncLog.entityId] = studentId
                it[SyncLog.operation] = SyncOperation.CREATE
                it[SyncLog.userId] = userId
            }

            getStudent(studentId, schoolId)!!
        }
    }

    /**
     * Create a new student with auto-generated ID and log to sync
     */
    fun createStudent(
        schoolId: UUID,
        firstName: String,
        lastName: String,
        userId: UUID
    ): StudentDto {
        return transaction {
            val studentId = Students.insertAndGetId {
                it[Students.schoolId] = schoolId
                it[Students.firstName] = firstName
                it[Students.lastName] = lastName
                it[Students.createdBy] = userId
            }.value

            // Log the creation to sync log
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.STUDENT.name
                it[SyncLog.entityId] = studentId
                it[SyncLog.operation] = SyncOperation.CREATE
                it[SyncLog.userId] = userId
            }

            Students.selectAll()
                .where { Students.id eq studentId }
                .first()
                .toStudentDto()
        }
    }

    /**
     * Update an existing student with optimistic locking
     */
    fun updateStudent(
        studentId: UUID,
        schoolId: UUID,
        firstName: String,
        lastName: String,
        version: Long,
        userId: UUID
    ): UpdateResult {
        return transaction {
            // Check if student exists and belongs to school
            val existing = Students.selectAll()
                .where { (Students.id eq studentId) and (Students.schoolId eq schoolId) }
                .firstOrNull() ?: return@transaction UpdateResult.NotFound

            // Optimistic locking check
            if (existing[Students.version] != version) {
                return@transaction UpdateResult.VersionConflict
            }

            // Update the student
            Students.update({ Students.id eq studentId }) {
                it[Students.firstName] = firstName
                it[Students.lastName] = lastName
                it[Students.version] = version + 1
                it[Students.updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
            }

            // Log the update to sync log
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.STUDENT.name
                it[SyncLog.entityId] = studentId
                it[SyncLog.operation] = SyncOperation.UPDATE
                it[SyncLog.userId] = userId
            }

            val updated = Students.selectAll()
                .where { Students.id eq studentId }
                .first()
                .toStudentDto()

            UpdateResult.Success(updated)
        }
    }

    /**
     * Delete a student
     */
    fun deleteStudent(studentId: UUID, schoolId: UUID, userId: UUID): Boolean {
        return transaction {
            // Check if student exists and belongs to school
            val existing = Students.selectAll()
                .where { (Students.id eq studentId) and (Students.schoolId eq schoolId) }
                .firstOrNull() ?: return@transaction false

            // Log the deletion to sync log BEFORE deleting
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.STUDENT.name
                it[SyncLog.entityId] = studentId
                it[SyncLog.operation] = SyncOperation.DELETE
                it[SyncLog.userId] = userId
            }

            // Delete the student
            val count = Students.deleteWhere {
                (Students.id eq studentId) and (Students.schoolId eq schoolId)
            }

            count > 0
        }
    }

    private fun ResultRow.toStudentDto() = StudentDto(
        id = this[Students.id].value.toString(),
        schoolId = this[Students.schoolId].value.toString(),
        firstName = this[Students.firstName],
        lastName = this[Students.lastName],
        classIds = emptyList(), // TODO: Load from student_classes junction table
        version = this[Students.version],
        createdAt = this[Students.createdAt].toString(),
        updatedAt = this[Students.updatedAt].toString()
    )
}

sealed class UpdateResult {
    data class Success(val data: StudentDto) : UpdateResult()
    object NotFound : UpdateResult()
    object VersionConflict : UpdateResult()
}
