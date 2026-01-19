package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto
import de.aarondietz.lehrerlog.db.tables.StudentClasses
import de.aarondietz.lehrerlog.db.tables.Students
import de.aarondietz.lehrerlog.db.tables.TaskSubmissions
import de.aarondietz.lehrerlog.db.tables.Tasks
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

class TaskSubmissionService {

    fun listSubmissions(taskId: UUID, schoolId: UUID): List<TaskSubmissionDto> = transaction {
        TaskSubmissions
            .innerJoin(Tasks)
            .selectAll()
            .where { (TaskSubmissions.taskId eq taskId) and (Tasks.schoolId eq schoolId) }
            .map { it.toDto() }
    }

    fun createSubmission(taskId: UUID, studentId: UUID, schoolId: UUID): TaskSubmissionDto = transaction {
        val task = Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .singleOrNull() ?: throw IllegalArgumentException("Task not found")

        val studentExists = Students.selectAll()
            .where { (Students.id eq studentId) and (Students.schoolId eq schoolId) }
            .any()
        if (!studentExists) {
            throw IllegalArgumentException("Student not found")
        }

        TaskSubmissions.insertIgnore {
            it[TaskSubmissions.taskId] = taskId
            it[TaskSubmissions.studentId] = studentId
        }

        TaskSubmissions
            .selectAll()
            .where { (TaskSubmissions.taskId eq taskId) and (TaskSubmissions.studentId eq studentId) }
            .single()
            .toDto()
    }

    fun getSummary(taskId: UUID, schoolId: UUID): TaskSubmissionSummaryDto = transaction {
        val task = Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .singleOrNull() ?: throw IllegalArgumentException("Task not found")

        val now = OffsetDateTime.now()

        val totalStudents = StudentClasses
            .selectAll()
            .where {
                (StudentClasses.schoolClassId eq task[Tasks.schoolClassId]) and
                    (StudentClasses.validTill.isNull() or (StudentClasses.validTill greater now))
            }
            .count()

        val submittedStudents = TaskSubmissions
            .selectAll()
            .where { TaskSubmissions.taskId eq taskId }
            .count()

        TaskSubmissionSummaryDto(
            taskId = taskId.toString(),
            totalStudents = totalStudents.toInt(),
            submittedStudents = submittedStudents.toInt()
        )
    }
}

private fun ResultRow.toDto(): TaskSubmissionDto {
    return TaskSubmissionDto(
        taskId = this[TaskSubmissions.taskId].value.toString(),
        studentId = this[TaskSubmissions.studentId].value.toString(),
        submittedAt = this[TaskSubmissions.submittedAt].toString(),
        createdAt = this[TaskSubmissions.createdAt].toString()
    )
}
