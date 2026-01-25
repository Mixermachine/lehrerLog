package de.aarondietz.lehrerlog.data

import kotlinx.serialization.Serializable

@Serializable
data class LatePeriodDto(
    val id: String,
    val name: String,
    val startsAt: String,
    val endsAt: String? = null,
    val isActive: Boolean,
    val createdAt: String
)

@Serializable
data class CreateLatePeriodRequest(
    val name: String,
    val startsAt: String,
    val endsAt: String? = null
)

@Serializable
data class UpdateLatePeriodRequest(
    val name: String? = null,
    val startsAt: String? = null,
    val endsAt: String? = null
)

@Serializable
data class LatePeriodSummaryDto(
    val periodId: String,
    val name: String,
    val startsAt: String,
    val endsAt: String? = null,
    val totalMissed: Int,
    val totalPunishments: Int
)

@Serializable
data class LateStudentStatsDto(
    val studentId: String,
    val totalMissed: Int,
    val missedSincePunishment: Int,
    val punishmentRequired: Boolean
)

@Serializable
data class PunishmentRecordDto(
    val id: String,
    val studentId: String,
    val periodId: String,
    val triggeredAt: String,
    val resolvedAt: String? = null,
    val resolvedBy: String? = null,
    val note: String? = null
)

@Serializable
data class ResolvePunishmentRequest(
    val studentId: String,
    val periodId: String,
    val note: String? = null
)
