package de.aarondietz.lehrerlog.services

import de.aarondietz.lehrerlog.data.StorageOwnerType as ApiStorageOwnerType
import de.aarondietz.lehrerlog.data.StorageQuotaDto
import de.aarondietz.lehrerlog.data.StorageUsageDto
import de.aarondietz.lehrerlog.db.tables.StorageOwnerType
import de.aarondietz.lehrerlog.db.tables.StoragePlans
import de.aarondietz.lehrerlog.db.tables.StorageSubscriptions
import de.aarondietz.lehrerlog.db.tables.StorageUsage
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class StorageService {

    fun getQuota(userId: UUID, schoolId: UUID): StorageQuotaDto = transaction {
        val resolved = resolveOwner(userId, schoolId)
        val usage = ensureUsageRow(resolved.ownerType, resolved.ownerId)

        val remaining = (resolved.planMaxTotalBytes - usage.usedTotalBytes).coerceAtLeast(0)
        StorageQuotaDto(
            ownerType = resolved.apiOwnerType,
            ownerId = resolved.ownerId.toString(),
            planId = resolved.planId.toString(),
            planName = resolved.planName,
            maxTotalBytes = resolved.planMaxTotalBytes,
            maxFileBytes = resolved.planMaxFileBytes,
            usedTotalBytes = usage.usedTotalBytes,
            remainingBytes = remaining
        )
    }

    fun getUsage(userId: UUID, schoolId: UUID): StorageUsageDto = transaction {
        val resolved = resolveOwner(userId, schoolId)
        val usage = ensureUsageRow(resolved.ownerType, resolved.ownerId)
        StorageUsageDto(
            ownerType = resolved.apiOwnerType,
            ownerId = resolved.ownerId.toString(),
            usedTotalBytes = usage.usedTotalBytes,
            updatedAt = usage.updatedAt.toString()
        )
    }

    fun reserveBytes(userId: UUID, schoolId: UUID, sizeBytes: Long): StorageQuotaDto = transaction {
        val resolved = resolveOwner(userId, schoolId)
        val usage = ensureUsageRow(resolved.ownerType, resolved.ownerId)
        val remaining = resolved.planMaxTotalBytes - usage.usedTotalBytes
        if (sizeBytes > resolved.planMaxFileBytes) {
            throw IllegalArgumentException("FILE_TOO_LARGE")
        }
        if (remaining < sizeBytes) {
            throw IllegalArgumentException("QUOTA_EXCEEDED")
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)
        StorageUsage.update({ (StorageUsage.ownerType eq resolved.ownerType.name) and (StorageUsage.ownerId eq resolved.ownerId) }) {
            it[usedTotalBytes] = usage.usedTotalBytes + sizeBytes
            it[updatedAt] = now
        }

        val updatedRemaining = (remaining - sizeBytes).coerceAtLeast(0)
        StorageQuotaDto(
            ownerType = resolved.apiOwnerType,
            ownerId = resolved.ownerId.toString(),
            planId = resolved.planId.toString(),
            planName = resolved.planName,
            maxTotalBytes = resolved.planMaxTotalBytes,
            maxFileBytes = resolved.planMaxFileBytes,
            usedTotalBytes = usage.usedTotalBytes + sizeBytes,
            remainingBytes = updatedRemaining
        )
    }

    fun releaseBytes(userId: UUID, schoolId: UUID, sizeBytes: Long) = transaction {
        val resolved = resolveOwner(userId, schoolId)
        val usage = ensureUsageRow(resolved.ownerType, resolved.ownerId)
        val newUsed = (usage.usedTotalBytes - sizeBytes).coerceAtLeast(0)
        StorageUsage.update({ (StorageUsage.ownerType eq resolved.ownerType.name) and (StorageUsage.ownerId eq resolved.ownerId) }) {
            it[usedTotalBytes] = newUsed
            it[updatedAt] = OffsetDateTime.now(ZoneOffset.UTC)
        }
    }

    private fun resolveOwner(userId: UUID, schoolId: UUID): ResolvedOwner {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val teacherSubscription = StorageSubscriptions.selectAll()
            .where {
                (StorageSubscriptions.ownerType eq StorageOwnerType.TEACHER.name) and
                    (StorageSubscriptions.ownerId eq userId) and
                    (StorageSubscriptions.active eq true) and
                    (StorageSubscriptions.endsAt.isNull() or (StorageSubscriptions.endsAt greater now))
            }
            .orderBy(StorageSubscriptions.startsAt, SortOrder.DESC)
            .firstOrNull()

        val subscriptionRow = teacherSubscription ?: StorageSubscriptions.selectAll()
            .where {
                (StorageSubscriptions.ownerType eq StorageOwnerType.SCHOOL.name) and
                    (StorageSubscriptions.ownerId eq schoolId) and
                    (StorageSubscriptions.active eq true) and
                    (StorageSubscriptions.endsAt.isNull() or (StorageSubscriptions.endsAt greater now))
            }
            .orderBy(StorageSubscriptions.startsAt, SortOrder.DESC)
            .firstOrNull()
            ?: throw IllegalStateException("No active storage subscription found")

        val ownerType = StorageOwnerType.valueOf(subscriptionRow[StorageSubscriptions.ownerType])
        val planId = subscriptionRow[StorageSubscriptions.planId]

        val planRow = StoragePlans.selectAll()
            .where { StoragePlans.id eq planId }
            .firstOrNull()
            ?: throw IllegalStateException("Storage plan not found")

        return ResolvedOwner(
            ownerType = ownerType,
            apiOwnerType = ApiStorageOwnerType.valueOf(ownerType.name),
            ownerId = subscriptionRow[StorageSubscriptions.ownerId],
            planId = planId,
            planName = planRow[StoragePlans.name],
            planMaxTotalBytes = planRow[StoragePlans.maxTotalBytes],
            planMaxFileBytes = planRow[StoragePlans.maxFileBytes]
        )
    }

    private fun ensureUsageRow(ownerType: StorageOwnerType, ownerId: UUID): UsageState {
        StorageUsage.insertIgnore {
            it[StorageUsage.ownerType] = ownerType.name
            it[StorageUsage.ownerId] = ownerId
            it[usedTotalBytes] = 0
        }

        val row = StorageUsage.selectAll()
            .where { (StorageUsage.ownerType eq ownerType.name) and (StorageUsage.ownerId eq ownerId) }
            .first()

        return UsageState(
            usedTotalBytes = row[StorageUsage.usedTotalBytes],
            updatedAt = row[StorageUsage.updatedAt]
        )
    }

    private data class ResolvedOwner(
        val ownerType: StorageOwnerType,
        val apiOwnerType: ApiStorageOwnerType,
        val ownerId: UUID,
        val planId: UUID,
        val planName: String,
        val planMaxTotalBytes: Long,
        val planMaxFileBytes: Long
    )

    private data class UsageState(
        val usedTotalBytes: Long,
        val updatedAt: OffsetDateTime
    )
}
