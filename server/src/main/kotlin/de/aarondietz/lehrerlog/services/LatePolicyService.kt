package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.*
import de.aarondietz.lehrerlog.db.tables.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class LatePolicyService {

    companion object {
        const val DEFAULT_THRESHOLD = 3
    }

    fun listPeriods(teacherId: UUID): List<LatePeriodDto> = transaction {
        LatePeriods.selectAll()
            .where { LatePeriods.teacherId eq teacherId }
            .orderBy(LatePeriods.startsAt, SortOrder.DESC)
            .map { it.toDto() }
    }

    fun createPeriod(teacherId: UUID, request: CreateLatePeriodRequest): LatePeriodDto = transaction {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val periodId = LatePeriods.insert {
            it[LatePeriods.id] = UUID.randomUUID()
            it[LatePeriods.teacherId] = teacherId
            it[LatePeriods.name] = request.name.trim()
            it[LatePeriods.startsAt] = OffsetDateTime.parse(request.startsAt)
            it[LatePeriods.endsAt] = request.endsAt?.let { end -> OffsetDateTime.parse(end) }
            it[LatePeriods.isActive] = false
            it[LatePeriods.createdAt] = now
        } get LatePeriods.id

        LatePeriods.selectAll()
            .where { LatePeriods.id eq periodId.value }
            .single()
            .toDto()
    }

    fun updatePeriod(teacherId: UUID, periodId: UUID, request: UpdateLatePeriodRequest): LatePeriodDto? = transaction {
        val existing = LatePeriods.selectAll()
            .where { (LatePeriods.id eq periodId) and (LatePeriods.teacherId eq teacherId) }
            .singleOrNull() ?: return@transaction null

        val updates = mutableListOf<Pair<org.jetbrains.exposed.sql.Column<*>, Any?>>()
        request.name?.trim()?.ifBlank { null }?.let { updates += LatePeriods.name to it }
        request.startsAt?.let { updates += LatePeriods.startsAt to OffsetDateTime.parse(it) }
        if (request.endsAt != null) {
            updates += LatePeriods.endsAt to request.endsAt?.let { end -> OffsetDateTime.parse(end) }
        }

        if (updates.isNotEmpty()) {
            LatePeriods.update({ LatePeriods.id eq periodId }) { row ->
                updates.forEach { (column, value) ->
                    @Suppress("UNCHECKED_CAST")
                    row[column as org.jetbrains.exposed.sql.Column<Any?>] = value
                }
            }
        }

        LatePeriods.selectAll()
            .where { LatePeriods.id eq periodId }
            .single()
            .toDto()
    }

    fun activatePeriod(teacherId: UUID, periodId: UUID): LatePeriodDto = transaction {
        LatePeriods.update({ LatePeriods.teacherId eq teacherId }) {
            it[LatePeriods.isActive] = false
        }
        LatePeriods.update({ (LatePeriods.id eq periodId) and (LatePeriods.teacherId eq teacherId) }) {
            it[LatePeriods.isActive] = true
        }

        LatePeriods.selectAll()
            .where { (LatePeriods.id eq periodId) and (LatePeriods.teacherId eq teacherId) }
            .single()
            .toDto()
    }

    fun ensureActivePeriod(teacherId: UUID): UUID = transaction {
        val existing = LatePeriods.selectAll()
            .where { (LatePeriods.teacherId eq teacherId) and (LatePeriods.isActive eq true) }
            .firstOrNull()
        if (existing != null) {
            return@transaction existing[LatePeriods.id].value
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val periodId = LatePeriods.insert {
            it[LatePeriods.id] = UUID.randomUUID()
            it[LatePeriods.teacherId] = teacherId
            it[LatePeriods.name] = "Current Period"
            it[LatePeriods.startsAt] = now
            it[LatePeriods.endsAt] = null
            it[LatePeriods.isActive] = true
            it[LatePeriods.createdAt] = now
        } get LatePeriods.id

        periodId.value
    }

    fun applyLateDecision(
        teacherId: UUID,
        studentId: UUID,
        lateStatus: LateStatus
    ): UUID = transaction {
        val periodId = ensureActivePeriod(teacherId)
        val threshold = ensurePolicy(teacherId)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val currentStats = StudentLateStats.selectAll()
            .where {
                (StudentLateStats.teacherId eq teacherId) and
                        (StudentLateStats.studentId eq studentId) and
                        (StudentLateStats.periodId eq periodId)
            }
            .singleOrNull()

        var totalMissed = currentStats?.get(StudentLateStats.totalMissed) ?: 0
        var missedSince = currentStats?.get(StudentLateStats.missedSincePunishment) ?: 0

        when (lateStatus) {
            LateStatus.LATE_PUNISH -> {
                totalMissed += 1
                missedSince += 1
            }

            LateStatus.LATE_FORGIVEN -> {
                totalMissed += 1
            }

            else -> return@transaction periodId
        }

        val punishmentRequired = missedSince >= threshold

        if (currentStats == null) {
            StudentLateStats.insert {
                it[StudentLateStats.teacherId] = teacherId
                it[StudentLateStats.studentId] = studentId
                it[StudentLateStats.periodId] = periodId
                it[StudentLateStats.totalMissed] = totalMissed
                it[StudentLateStats.missedSincePunishment] = missedSince
                it[StudentLateStats.punishmentRequired] = punishmentRequired
                it[StudentLateStats.updatedAt] = now
            }
        } else {
            StudentLateStats.update({
                (StudentLateStats.teacherId eq teacherId) and
                        (StudentLateStats.studentId eq studentId) and
                        (StudentLateStats.periodId eq periodId)
            }) {
                it[StudentLateStats.totalMissed] = totalMissed
                it[StudentLateStats.missedSincePunishment] = missedSince
                it[StudentLateStats.punishmentRequired] = punishmentRequired
                it[StudentLateStats.updatedAt] = now
            }
        }

        if (punishmentRequired) {
            val existingRecord = PunishmentRecords.selectAll()
                .where {
                    (PunishmentRecords.teacherId eq teacherId) and
                            (PunishmentRecords.studentId eq studentId) and
                            (PunishmentRecords.periodId eq periodId) and
                            PunishmentRecords.resolvedAt.isNull()
                }
                .firstOrNull()
            if (existingRecord == null) {
                PunishmentRecords.insert {
                    it[PunishmentRecords.id] = UUID.randomUUID()
                    it[PunishmentRecords.teacherId] = teacherId
                    it[PunishmentRecords.studentId] = studentId
                    it[PunishmentRecords.periodId] = periodId
                    it[PunishmentRecords.triggeredAt] = now
                    it[PunishmentRecords.resolvedAt] = null
                    it[PunishmentRecords.resolvedBy] = null
                    it[PunishmentRecords.note] = null
                }
            }
        }

        periodId
    }

    fun getPeriodSummaries(teacherId: UUID): List<LatePeriodSummaryDto> = transaction {
        val statsByPeriod = StudentLateStats.selectAll()
            .where { StudentLateStats.teacherId eq teacherId }
            .groupBy { it[StudentLateStats.periodId].value }

        LatePeriods.selectAll()
            .where { LatePeriods.teacherId eq teacherId }
            .map { row ->
                val periodId = row[LatePeriods.id].value
                val stats = statsByPeriod[periodId].orEmpty()
                val totalMissed = stats.sumOf { it[StudentLateStats.totalMissed] }
                val totalPunishments = stats.count { it[StudentLateStats.punishmentRequired] }
                LatePeriodSummaryDto(
                    periodId = periodId.toString(),
                    name = row[LatePeriods.name],
                    startsAt = row[LatePeriods.startsAt].toString(),
                    endsAt = row[LatePeriods.endsAt]?.toString(),
                    totalMissed = totalMissed,
                    totalPunishments = totalPunishments
                )
            }
    }

    fun getStatsForPeriod(teacherId: UUID, periodId: UUID): List<LateStudentStatsDto> = transaction {
        StudentLateStats.selectAll()
            .where { (StudentLateStats.teacherId eq teacherId) and (StudentLateStats.periodId eq periodId) }
            .map { row ->
                LateStudentStatsDto(
                    studentId = row[StudentLateStats.studentId].value.toString(),
                    totalMissed = row[StudentLateStats.totalMissed],
                    missedSincePunishment = row[StudentLateStats.missedSincePunishment],
                    punishmentRequired = row[StudentLateStats.punishmentRequired]
                )
            }
    }

    fun getStatsForStudent(teacherId: UUID, studentId: UUID, periodId: UUID): LateStudentStatsDto? = transaction {
        StudentLateStats.selectAll()
            .where {
                (StudentLateStats.teacherId eq teacherId) and
                        (StudentLateStats.studentId eq studentId) and
                        (StudentLateStats.periodId eq periodId)
            }
            .singleOrNull()
            ?.let { row ->
                LateStudentStatsDto(
                    studentId = row[StudentLateStats.studentId].value.toString(),
                    totalMissed = row[StudentLateStats.totalMissed],
                    missedSincePunishment = row[StudentLateStats.missedSincePunishment],
                    punishmentRequired = row[StudentLateStats.punishmentRequired]
                )
            }
    }

    fun resolvePunishment(teacherId: UUID, resolvedBy: UUID, request: ResolvePunishmentRequest): PunishmentRecordDto? =
        transaction {
            val studentId = UUID.fromString(request.studentId)
            val periodId = UUID.fromString(request.periodId)
            val now = OffsetDateTime.now(ZoneOffset.UTC)

            val record = PunishmentRecords.selectAll()
                .where {
                    (PunishmentRecords.teacherId eq teacherId) and
                            (PunishmentRecords.studentId eq studentId) and
                            (PunishmentRecords.periodId eq periodId) and
                            PunishmentRecords.resolvedAt.isNull()
                }
                .firstOrNull() ?: return@transaction null

            PunishmentRecords.update({ PunishmentRecords.id eq record[PunishmentRecords.id].value }) {
                it[PunishmentRecords.resolvedAt] = now
                it[PunishmentRecords.resolvedBy] = resolvedBy
                it[PunishmentRecords.note] = request.note?.trim()?.ifBlank { null }
            }

            StudentLateStats.update({
                (StudentLateStats.teacherId eq teacherId) and
                        (StudentLateStats.studentId eq studentId) and
                        (StudentLateStats.periodId eq periodId)
            }) {
                it[StudentLateStats.missedSincePunishment] = 0
                it[StudentLateStats.punishmentRequired] = false
                it[StudentLateStats.updatedAt] = now
            }

            recordToDto(record)
        }

    fun getPunishments(teacherId: UUID, studentId: UUID, periodId: UUID): List<PunishmentRecordDto> = transaction {
        PunishmentRecords.selectAll()
            .where {
                (PunishmentRecords.teacherId eq teacherId) and
                        (PunishmentRecords.studentId eq studentId) and
                        (PunishmentRecords.periodId eq periodId)
            }
            .orderBy(PunishmentRecords.triggeredAt, SortOrder.DESC)
            .map { recordToDto(it) }
    }

    fun recalculatePeriod(teacherId: UUID, periodId: UUID) = transaction {
        val threshold = ensurePolicy(teacherId)
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        StudentLateStats.deleteWhere { (StudentLateStats.teacherId eq teacherId) and (StudentLateStats.periodId eq periodId) }

        val decisions = TaskSubmissions.selectAll()
            .where {
                (TaskSubmissions.latePeriodId eq periodId) and (TaskSubmissions.lateStatus inList listOf(
                    LateStatus.LATE_PUNISH.name,
                    LateStatus.LATE_FORGIVEN.name
                ))
            }
            .map { row -> row[TaskSubmissions.studentId].value to LateStatus.valueOf(row[TaskSubmissions.lateStatus]) }

        val grouped = decisions.groupBy { it.first }
        grouped.forEach { (studentId, statuses) ->
            val totalMissed = statuses.size
            val missedSince = statuses.count { it.second == LateStatus.LATE_PUNISH }
            val punishmentRequired = missedSince >= threshold
            StudentLateStats.insert {
                it[StudentLateStats.teacherId] = teacherId
                it[StudentLateStats.studentId] = studentId
                it[StudentLateStats.periodId] = periodId
                it[StudentLateStats.totalMissed] = totalMissed
                it[StudentLateStats.missedSincePunishment] = missedSince
                it[StudentLateStats.punishmentRequired] = punishmentRequired
                it[StudentLateStats.updatedAt] = now
            }
        }
    }

    private fun ensurePolicy(teacherId: UUID): Int {
        TeacherLatePolicy.insertIgnore {
            it[TeacherLatePolicy.teacherId] = teacherId
            it[TeacherLatePolicy.threshold] = DEFAULT_THRESHOLD
        }

        return TeacherLatePolicy.selectAll()
            .where { TeacherLatePolicy.teacherId eq teacherId }
            .first()[TeacherLatePolicy.threshold]
    }

    private fun recordToDto(row: org.jetbrains.exposed.sql.ResultRow): PunishmentRecordDto {
        return PunishmentRecordDto(
            id = row[PunishmentRecords.id].value.toString(),
            studentId = row[PunishmentRecords.studentId].value.toString(),
            periodId = row[PunishmentRecords.periodId].value.toString(),
            triggeredAt = row[PunishmentRecords.triggeredAt].toString(),
            resolvedAt = row[PunishmentRecords.resolvedAt]?.toString(),
            resolvedBy = row[PunishmentRecords.resolvedBy]?.value?.toString(),
            note = row[PunishmentRecords.note]
        )
    }
}

private fun org.jetbrains.exposed.sql.ResultRow.toDto(): LatePeriodDto {
    return LatePeriodDto(
        id = this[LatePeriods.id].value.toString(),
        name = this[LatePeriods.name],
        startsAt = this[LatePeriods.startsAt].toString(),
        endsAt = this[LatePeriods.endsAt]?.toString(),
        isActive = this[LatePeriods.isActive],
        createdAt = this[LatePeriods.createdAt].toString()
    )
}
