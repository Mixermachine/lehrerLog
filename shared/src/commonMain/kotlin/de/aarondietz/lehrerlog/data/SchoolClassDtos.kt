package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
data class SchoolClassDto(
    val id: String,
    val schoolId: String,
    val name: String,
    val alternativeName: String? = null,
    val studentCount: Int = 0,
    val version: Long,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CreateSchoolClassRequest(
    val name: String,
    val alternativeName: String? = null
)

@Serializable
data class UpdateSchoolClassRequest(
    val name: String,
    val alternativeName: String? = null,
    val version: Long
)
