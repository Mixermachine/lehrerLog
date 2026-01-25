package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object StorageUsage : Table("storage_usage") {
    val ownerType = varchar("owner_type", 20)
    val ownerId = uuid("owner_id")
    val usedTotalBytes = long("used_total_bytes").default(0)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(ownerType, ownerId)
}
