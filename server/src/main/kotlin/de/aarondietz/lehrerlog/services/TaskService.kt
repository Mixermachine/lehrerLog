package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.TaskDto
import de.aarondietz.lehrerlog.db.tables.SchoolClasses
import de.aarondietz.lehrerlog.db.tables.Tasks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID

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
            .where { (SchoolClasses.id eq classId) and (SchoolClasses.schoolId eq schoolId) }
            .any()
        if (!classExists) {
            throw IllegalArgumentException("Class not found")
        }

        val dueAtParsed = parseDueAt(dueAt)

        val taskId = Tasks.insertAndGetId {
            it[Tasks.schoolId] = schoolId
            it[Tasks.schoolClassId] = classId
            it[Tasks.title] = title
            it[Tasks.description] = description
            it[Tasks.dueAt] = dueAtParsed
            it[Tasks.createdBy] = userId
        }.value

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
