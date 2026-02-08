package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.db.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.*

class TaskService {

    fun getTasksByClass(schoolId: UUID, classId: UUID): List<TaskDto> = transaction {
        Tasks.selectAll()
            .where { (Tasks.schoolId eq schoolId) and (Tasks.schoolClassId eq classId) }
            .orderBy(Tasks.dueAt, SortOrder.ASC)
            .map { it.toDto() }
    }

    fun createTask(
        schoolId: UUID,
        classId: UUID,
        title: String,
        description: String?,
        dueAt: String,
        userId: UUID
    ): TaskDto = transaction {
        val classExists = SchoolClasses.selectAll()
            .where {
                (SchoolClasses.id eq classId) and
                        (SchoolClasses.schoolId eq schoolId) and
                        SchoolClasses.archivedAt.isNull()
            }
            .any()
        if (!classExists) {
            throw IllegalArgumentException("Class not found")
        }

        val dueAtParsed = parseDueAt(dueAt)
        val now = OffsetDateTime.now()

        val taskId = Tasks.insertAndGetId {
            it[Tasks.schoolId] = schoolId
            it[Tasks.schoolClassId] = classId
            it[Tasks.title] = title
            it[Tasks.description] = description
            it[Tasks.dueAt] = dueAtParsed
            it[Tasks.createdBy] = userId
        }.value

        StudentClasses
            .innerJoin(Students)
            .selectAll()
            .where {
                (StudentClasses.schoolClassId eq classId) and
                        (StudentClasses.validFrom lessEq now) and
                        (StudentClasses.validTill.isNull() or (StudentClasses.validTill greater now)) and
                        Students.deletedAt.isNull()
            }
            .forEach { row ->
                TaskTargets.insertIgnore {
                    it[TaskTargets.taskId] = taskId
                    it[TaskTargets.studentId] = row[StudentClasses.studentId].value
                }
            }

        Tasks.selectAll()
            .where { Tasks.id eq taskId }
            .single()
            .toDto()
    }

    fun getTask(taskId: UUID, schoolId: UUID): TaskDto? = transaction {
        Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .singleOrNull()
            ?.toDto()
    }

    fun getTasksByStudent(schoolId: UUID, studentId: UUID): List<TaskDto> = transaction {
        Tasks
            .innerJoin(TaskTargets)
            .selectAll()
            .where { (Tasks.schoolId eq schoolId) and (TaskTargets.studentId eq studentId) }
            .orderBy(Tasks.dueAt, SortOrder.ASC)
            .map { it.toDto() }
    }

    fun updateTask(
        taskId: UUID,
        schoolId: UUID,
        title: String?,
        description: String?,
        dueAt: String?
    ): TaskDto = transaction {
        val existing = Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .singleOrNull() ?: throw IllegalArgumentException("Task not found")

        val updates = mutableListOf<Pair<Column<*>, Any?>>()
        title?.trim()?.ifBlank { null }?.let { updates += Tasks.title to it }
        if (description != null) {
            updates += Tasks.description to description.trim().ifBlank { null }
        }
        dueAt?.trim()?.let { updates += Tasks.dueAt to parseDueAt(it) }

        if (updates.isNotEmpty()) {
            Tasks.update({ Tasks.id eq taskId }) { row ->
                updates.forEach { (column, value) ->
                    @Suppress("UNCHECKED_CAST")
                    row[column as Column<Any?>] = value
                }
            }
        }

        Tasks.selectAll()
            .where { Tasks.id eq taskId }
            .single()
            .toDto()
    }

    fun deleteTask(taskId: UUID, schoolId: UUID) = transaction {
        val deleted = Tasks.deleteWhere { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
        if (deleted == 0) {
            throw IllegalArgumentException("Task not found")
        }
    }

    fun updateTargets(
        taskId: UUID,
        schoolId: UUID,
        addStudentIds: List<UUID>,
        removeStudentIds: List<UUID>
    ) = transaction {
        val taskExists = Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .any()
        if (!taskExists) {
            throw IllegalArgumentException("Task not found")
        }

        if (addStudentIds.isNotEmpty()) {
            val validStudents = Students.selectAll()
                .where {
                    (Students.schoolId eq schoolId) and
                            (Students.id inList addStudentIds) and
                            Students.deletedAt.isNull()
                }
                .map { it[Students.id].value }
                .toSet()
            val invalid = addStudentIds.filterNot { it in validStudents }
            if (invalid.isNotEmpty()) {
                throw IllegalArgumentException("Student not found")
            }
            addStudentIds.forEach { studentId ->
                TaskTargets.insertIgnore {
                    it[TaskTargets.taskId] = taskId
                    it[TaskTargets.studentId] = studentId
                }
            }
        }

        if (removeStudentIds.isNotEmpty()) {
            TaskTargets.deleteWhere {
                (TaskTargets.taskId eq taskId) and (TaskTargets.studentId inList removeStudentIds)
            }
        }
    }

    fun getTargetStudentIds(taskId: UUID, schoolId: UUID): List<UUID> = transaction {
        val taskExists = Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .any()
        if (!taskExists) {
            throw IllegalArgumentException("Task not found")
        }

        TaskTargets
            .select(TaskTargets.studentId)
            .where { TaskTargets.taskId eq taskId }
            .map { it[TaskTargets.studentId].value }
    }

    private fun parseDueAt(dueAt: String): OffsetDateTime {
        return try {
            OffsetDateTime.parse(dueAt)
        } catch (e: DateTimeParseException) {
            val date = LocalDate.parse(dueAt)
            date.atTime(23, 59, 59).atOffset(ZoneOffset.UTC)
        }
    }
}

private fun ResultRow.toDto(): TaskDto {
    return TaskDto(
        id = this[Tasks.id].value.toString(),
        schoolId = this[Tasks.schoolId].value.toString(),
        schoolClassId = this[Tasks.schoolClassId].value.toString(),
        title = this[Tasks.title],
        description = this[Tasks.description],
        dueAt = this[Tasks.dueAt].toString(),
        version = this[Tasks.version],
        createdAt = this[Tasks.createdAt].toString(),
        updatedAt = this[Tasks.updatedAt].toString()
    )
}
