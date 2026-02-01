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
            val students = Students.selectAll()
                .where { (Students.schoolId eq schoolId) and Students.deletedAt.isNull() }
                .toList()
            val studentIds = students.map { it[Students.id].value }
            val classIdsByStudent = loadClassIdsByStudent(schoolId, studentIds)
            students.map { row ->
                val classIds = classIdsByStudent[row[Students.id].value].orEmpty()
                row.toStudentDto(classIds)
            }
        }
    }

    /**
     * Get a specific student by ID (with school validation)
     */
    fun getStudent(studentId: UUID, schoolId: UUID): StudentDto? {
        return transaction {
            Students.selectAll()
                .where {
                    (Students.id eq studentId) and
                            (Students.schoolId eq schoolId) and
                            Students.deletedAt.isNull()
                }
                .firstOrNull()
                ?.let { row ->
                    val classIds = loadClassIdsByStudent(schoolId, listOf(studentId))[studentId].orEmpty()
                    row.toStudentDto(classIds)
                }
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
        classIds: List<String>,
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

            assignStudentClasses(studentId, schoolId, classIds)

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
        classIds: List<String>,
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

            assignStudentClasses(studentId, schoolId, classIds)

            Students.selectAll()
                .where { Students.id eq studentId }
                .first()
                .toStudentDto(loadClassIdsByStudent(schoolId, listOf(studentId))[studentId].orEmpty())
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
        classIds: List<String>,
        version: Long,
        userId: UUID
    ): UpdateResult {
        return transaction {
            // Check if student exists and belongs to school
            val existing = Students.selectAll()
                .where {
                    (Students.id eq studentId) and
                            (Students.schoolId eq schoolId) and
                            Students.deletedAt.isNull()
                }
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

            assignStudentClasses(studentId, schoolId, classIds)

            val updated = Students.selectAll()
                .where { Students.id eq studentId }
                .first()
                .toStudentDto(loadClassIdsByStudent(schoolId, listOf(studentId))[studentId].orEmpty())

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
                .where {
                    (Students.id eq studentId) and
                            (Students.schoolId eq schoolId) and
                            Students.deletedAt.isNull()
                }
                .firstOrNull() ?: return@transaction false

            // Log the deletion to sync log BEFORE deleting
            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.STUDENT.name
                it[SyncLog.entityId] = studentId
                it[SyncLog.operation] = SyncOperation.DELETE
                it[SyncLog.userId] = userId
            }

            val now = OffsetDateTime.now(ZoneOffset.UTC)
            val count = Students.update({ Students.id eq studentId }) {
                it[Students.deletedAt] = now
                it[Students.updatedAt] = now
                it[Students.version] = existing[Students.version] + 1
            }

            count > 0
        }
    }

    fun addStudentToClass(
        studentId: UUID,
        classId: UUID,
        schoolId: UUID,
        userId: UUID
    ): StudentDto? = transaction {
        val studentRow = Students.selectAll()
            .where {
                (Students.id eq studentId) and
                        (Students.schoolId eq schoolId) and
                        Students.deletedAt.isNull()
            }
            .firstOrNull() ?: return@transaction null

        val classExists = SchoolClasses.selectAll()
            .where {
                (SchoolClasses.id eq classId) and
                        (SchoolClasses.schoolId eq schoolId) and
                        SchoolClasses.archivedAt.isNull()
            }
            .any()
        if (!classExists) {
            return@transaction null
        }

        val alreadyAssigned = StudentClasses.selectAll()
            .where {
                (StudentClasses.studentId eq studentId) and
                        (StudentClasses.schoolClassId eq classId) and
                        StudentClasses.validTill.isNull()
            }
            .any()

        if (!alreadyAssigned) {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            StudentClasses.insert {
                it[StudentClasses.studentId] = studentId
                it[StudentClasses.schoolClassId] = classId
                it[StudentClasses.validFrom] = now
                it[StudentClasses.validTill] = null
            }

            Students.update({ Students.id eq studentId }) {
                it[Students.version] = studentRow[Students.version] + 1
                it[Students.updatedAt] = now
            }

            SyncLog.insert {
                it[SyncLog.schoolId] = schoolId
                it[SyncLog.entityType] = EntityType.STUDENT.name
                it[SyncLog.entityId] = studentId
                it[SyncLog.operation] = SyncOperation.UPDATE
                it[SyncLog.userId] = userId
            }
        }

        getStudent(studentId, schoolId)
    }

    fun removeStudentFromClass(
        studentId: UUID,
        classId: UUID,
        schoolId: UUID,
        userId: UUID
    ): StudentDto? = transaction {
        val studentRow = Students.selectAll()
            .where {
                (Students.id eq studentId) and
                        (Students.schoolId eq schoolId) and
                        Students.deletedAt.isNull()
            }
            .firstOrNull() ?: return@transaction null

        val classExists = SchoolClasses.selectAll()
            .where {
                (SchoolClasses.id eq classId) and
                        (SchoolClasses.schoolId eq schoolId) and
                        SchoolClasses.archivedAt.isNull()
            }
            .any()
        if (!classExists) {
            return@transaction null
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = StudentClasses.update({
            (StudentClasses.studentId eq studentId) and
                    (StudentClasses.schoolClassId eq classId) and
                    StudentClasses.validTill.isNull()
        }) {
            it[StudentClasses.validTill] = now
        }

        if (updated == 0) {
            return@transaction null
        }

        Students.update({ Students.id eq studentId }) {
            it[Students.version] = studentRow[Students.version] + 1
            it[Students.updatedAt] = now
        }

        SyncLog.insert {
            it[SyncLog.schoolId] = schoolId
            it[SyncLog.entityType] = EntityType.STUDENT.name
            it[SyncLog.entityId] = studentId
            it[SyncLog.operation] = SyncOperation.UPDATE
            it[SyncLog.userId] = userId
        }

        getStudent(studentId, schoolId)
    }

    private fun ResultRow.toStudentDto(classIds: List<String>) = StudentDto(
        id = this[Students.id].value.toString(),
        schoolId = this[Students.schoolId].value.toString(),
        firstName = this[Students.firstName],
        lastName = this[Students.lastName],
        classIds = classIds,
        version = this[Students.version],
        createdAt = this[Students.createdAt].toString(),
        updatedAt = this[Students.updatedAt].toString()
    )

    private fun assignStudentClasses(studentId: UUID, schoolId: UUID, classIds: List<String>) {
        val validClassIds = loadValidClassIds(schoolId, classIds)
        StudentClasses.deleteWhere { StudentClasses.studentId eq studentId }
        if (validClassIds.isEmpty()) {
            return
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        validClassIds.forEach { classId ->
            StudentClasses.insert {
                it[StudentClasses.studentId] = studentId
                it[StudentClasses.schoolClassId] = classId
                it[StudentClasses.validFrom] = now
                it[StudentClasses.validTill] = null
            }
        }
    }

    private fun loadValidClassIds(schoolId: UUID, classIds: List<String>): List<UUID> {
        if (classIds.isEmpty()) return emptyList()
        val parsedIds = classIds.mapNotNull {
            try {
                UUID.fromString(it)
            } catch (e: Exception) {
                null
            }
        }
        if (parsedIds.isEmpty()) return emptyList()
        return SchoolClasses
            .selectAll()
            .where {
                (SchoolClasses.id inList parsedIds) and
                        (SchoolClasses.schoolId eq schoolId) and
                        SchoolClasses.archivedAt.isNull()
            }
            .map { it[SchoolClasses.id].value }
    }

    private fun loadClassIdsByStudent(
        schoolId: UUID,
        studentIds: List<UUID>
    ): Map<UUID, List<String>> {
        if (studentIds.isEmpty()) return emptyMap()
        val result = mutableMapOf<UUID, MutableList<String>>()
        StudentClasses
            .innerJoin(SchoolClasses)
            .innerJoin(Students)
            .select(StudentClasses.studentId, StudentClasses.schoolClassId)
            .where {
                (StudentClasses.studentId inList studentIds) and
                        (SchoolClasses.schoolId eq schoolId) and
                        StudentClasses.validTill.isNull() and
                        SchoolClasses.archivedAt.isNull() and
                        Students.deletedAt.isNull()
            }
            .forEach { row ->
                val studentId = row[StudentClasses.studentId].value
                val classId = row[StudentClasses.schoolClassId].value.toString()
                result.getOrPut(studentId) { mutableListOf() }.add(classId)
            }

        return result.mapValues { it.value.distinct() }
    }
}

sealed class UpdateResult {
    data class Success(val data: StudentDto) : UpdateResult()
    object NotFound : UpdateResult()
    object VersionConflict : UpdateResult()
}
