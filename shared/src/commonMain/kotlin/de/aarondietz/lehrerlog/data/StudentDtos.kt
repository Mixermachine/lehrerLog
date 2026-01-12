package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
data class StudentDto(
    val id: String,
    val schoolId: String,
    val firstName: String,
    val lastName: String,
    val classIds: List<String> = emptyList(),
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateStudentRequest(
    val firstName: String,
    val lastName: String,
    val classIds: List<String> = emptyList()
)

@Serializable
data class UpdateStudentRequest(
    val firstName: String,
    val lastName: String,
    val classIds: List<String> = emptyList(),
    val version: Long
)

@Serializable
data class StudentClassAssignment(
    val studentId: String,
    val classId: String,
    val validFrom: String,
    val validTill: String? = null
)
