package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.LateStatus
import de.aarondietz.lehrerlog.data.TaskSubmissionDto
import de.aarondietz.lehrerlog.data.TaskSubmissionSummaryDto
import de.aarondietz.lehrerlog.data.TaskSubmissionType
import de.aarondietz.lehrerlog.db.tables.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class TaskSubmissionService {

    class DuplicateSubmissionException(message: String) : IllegalStateException(message)

    private val latePolicyService = LatePolicyService()

    fun listSubmissions(taskId: UUID, schoolId: UUID): List<TaskSubmissionDto> = transaction {
        TaskSubmissions
            .innerJoin(Tasks)
            .selectAll()
            .where { (TaskSubmissions.taskId eq taskId) and (Tasks.schoolId eq schoolId) }
            .map { it.toDto() }
    }

    fun listSubmissionsByStudent(studentId: UUID, schoolId: UUID): List<TaskSubmissionDto> = transaction {
        TaskSubmissions
            .innerJoin(Tasks)
            .selectAll()
            .where { (TaskSubmissions.studentId eq studentId) and (Tasks.schoolId eq schoolId) }
            .map { it.toDto() }
    }

    fun createSubmission(
        taskId: UUID,
        studentId: UUID,
        schoolId: UUID,
        submissionType: TaskSubmissionType,
        grade: Double?,
        note: String?
    ): TaskSubmissionDto = transaction {
        val task = Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .singleOrNull() ?: throw IllegalArgumentException("Task not found")

        val isTargeted = TaskTargets
            .selectAll()
            .where { (TaskTargets.taskId eq taskId) and (TaskTargets.studentId eq studentId) }
            .any()
        if (!isTargeted) {
            throw IllegalArgumentException("Student not targeted for task")
        }

        val studentExists = Students.selectAll()
            .where {
                (Students.id eq studentId) and
                        (Students.schoolId eq schoolId) and
                        Students.deletedAt.isNull()
            }
            .any()
        if (!studentExists) {
            throw IllegalArgumentException("Student not found")
        }

        val existing = TaskSubmissions.selectAll()
            .where { (TaskSubmissions.taskId eq taskId) and (TaskSubmissions.studentId eq studentId) }
            .singleOrNull()
        if (existing != null) {
            throw DuplicateSubmissionException("Submission already exists")
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val lateStatus = if (now.isAfter(task[Tasks.dueAt])) {
            LateStatus.LATE_UNDECIDED
        } else {
            LateStatus.ON_TIME
        }

        TaskSubmissions.insert {
            it[TaskSubmissions.taskId] = taskId
            it[TaskSubmissions.studentId] = studentId
            it[TaskSubmissions.submissionType] = submissionType.name
            it[TaskSubmissions.grade] = grade?.toBigDecimal()
            it[TaskSubmissions.note] = note
            it[TaskSubmissions.lateStatus] = lateStatus.name
        }

        TaskSubmissions
            .selectAll()
            .where { (TaskSubmissions.taskId eq taskId) and (TaskSubmissions.studentId eq studentId) }
            .single()
            .toDto()
    }

    fun updateSubmission(
        submissionId: UUID,
        schoolId: UUID,
        grade: Double?,
        note: String?,
        lateStatus: LateStatus?,
        decidedBy: UUID?
    ): TaskSubmissionDto = transaction {
        val submissionRow = TaskSubmissions
            .innerJoin(Tasks)
            .selectAll()
            .where { (TaskSubmissions.id eq submissionId) and (Tasks.schoolId eq schoolId) }
            .singleOrNull() ?: throw IllegalArgumentException("Submission not found")

        val updates = mutableListOf<Pair<Column<*>, Any?>>()
        if (grade != null) {
            updates += TaskSubmissions.grade to grade.toBigDecimal()
        }
        if (note != null) {
            updates += TaskSubmissions.note to note.trim().ifBlank { null }
        }
        if (lateStatus != null) {
            val currentLateStatus = LateStatus.valueOf(submissionRow[TaskSubmissions.lateStatus])
            if (lateStatus == LateStatus.LATE_PUNISH || lateStatus == LateStatus.LATE_FORGIVEN) {
                if (currentLateStatus != LateStatus.LATE_UNDECIDED) {
                    throw IllegalArgumentException("Late decision already made")
                }
                val teacherId = decidedBy ?: throw IllegalArgumentException("decidedBy is required")
                val periodId = latePolicyService.applyLateDecision(
                    teacherId = teacherId,
                    studentId = submissionRow[TaskSubmissions.studentId].value,
                    lateStatus = lateStatus
                )
                updates += TaskSubmissions.lateStatus to lateStatus.name
                updates += TaskSubmissions.latePeriodId to EntityID(periodId, LatePeriods)
                updates += TaskSubmissions.decidedBy to EntityID(teacherId, Users)
                updates += TaskSubmissions.decidedAt to OffsetDateTime.now(ZoneOffset.UTC)
            } else {
                updates += TaskSubmissions.lateStatus to lateStatus.name
            }
        }
        if (updates.isEmpty()) {
            return@transaction submissionRow.toDto()
        }

        TaskSubmissions.update({ TaskSubmissions.id eq submissionId }) { row ->
            updates.forEach { (column, value) ->
                @Suppress("UNCHECKED_CAST")
                row[column as Column<Any?>] = value
            }
        }

        TaskSubmissions
            .selectAll()
            .where { TaskSubmissions.id eq submissionId }
            .single()
            .toDto()
    }

    fun getSummary(taskId: UUID, schoolId: UUID): TaskSubmissionSummaryDto = transaction {
        val task = Tasks.selectAll()
            .where { (Tasks.id eq taskId) and (Tasks.schoolId eq schoolId) }
            .singleOrNull() ?: throw IllegalArgumentException("Task not found")

        val totalStudents = TaskTargets
            .selectAll()
            .where { TaskTargets.taskId eq taskId }
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
        id = this[TaskSubmissions.id].toString(),
        taskId = this[TaskSubmissions.taskId].value.toString(),
        studentId = this[TaskSubmissions.studentId].value.toString(),
        submissionType = TaskSubmissionType.valueOf(this[TaskSubmissions.submissionType]),
        grade = this[TaskSubmissions.grade]?.toDouble(),
        note = this[TaskSubmissions.note],
        lateStatus = LateStatus.valueOf(this[TaskSubmissions.lateStatus]),
        latePeriodId = this[TaskSubmissions.latePeriodId]?.value?.toString(),
        decidedBy = this[TaskSubmissions.decidedBy]?.value?.toString(),
        decidedAt = this[TaskSubmissions.decidedAt]?.toString(),
        submittedAt = this[TaskSubmissions.submittedAt].toString(),
        createdAt = this[TaskSubmissions.createdAt].toString(),
        updatedAt = this[TaskSubmissions.updatedAt].toString()
    )
}
