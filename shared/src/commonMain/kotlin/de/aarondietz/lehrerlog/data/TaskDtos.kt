package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    val id: String,
    val schoolId: String,
    val schoolClassId: String,
    val title: String,
    val description: String? = null,
    val dueAt: String,
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateTaskRequest(
    val schoolClassId: String,
    val title: String,
    val description: String? = null,
    val dueAt: String
)

@Serializable
data class UpdateTaskRequest(
    val title: String? = null,
    val description: String? = null,
    val dueAt: String? = null
)

@Serializable
data class TaskTargetsRequest(
    val addStudentIds: List<String> = emptyList(),
    val removeStudentIds: List<String> = emptyList()
)

@Serializable
data class TaskSubmissionDto(
    val id: String,
    val taskId: String,
    val studentId: String,
    val submissionType: TaskSubmissionType = TaskSubmissionType.FILE,
    val grade: Double? = null,
    val note: String? = null,
    val lateStatus: LateStatus = LateStatus.ON_TIME,
    val latePeriodId: String? = null,
    val decidedBy: String? = null,
    val decidedAt: String? = null,
    val submittedAt: String,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateTaskSubmissionRequest(
    val studentId: String,
    val submissionType: TaskSubmissionType = TaskSubmissionType.FILE,
    val grade: Double? = null,
    val note: String? = null
)

@Serializable
data class UpdateTaskSubmissionRequest(
    val grade: Double? = null,
    val note: String? = null,
    val lateStatus: LateStatus? = null
)

@Serializable
data class TaskSubmissionSummaryDto(
    val taskId: String,
    val totalStudents: Int,
    val submittedStudents: Int
)

@Serializable
enum class TaskSubmissionType {
    FILE,
    IN_PERSON
}

@Serializable
enum class LateStatus {
    ON_TIME,
    LATE_UNDECIDED,
    LATE_FORGIVEN,
    LATE_PUNISH
}
