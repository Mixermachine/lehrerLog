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
data class TaskSubmissionDto(
    val taskId: String,
    val studentId: String,
    val submittedAt: String,
    val createdAt: String
)

@Serializable
data class CreateTaskSubmissionRequest(
    val studentId: String
)

@Serializable
data class TaskSubmissionSummaryDto(
    val taskId: String,
    val totalStudents: Int,
    val submittedStudents: Int
)
