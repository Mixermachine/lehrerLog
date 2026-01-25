package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.util.UUID

object StoragePlans : Table("storage_plans") {
    val id = uuid("id").clientDefault { UUID.randomUUID() }.uniqueIndex()
    val name = text("name")
    val maxTotalBytes = long("max_total_bytes")
    val maxFileBytes = long("max_file_bytes")
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
