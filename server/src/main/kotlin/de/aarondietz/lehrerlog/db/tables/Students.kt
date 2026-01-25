package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Students : UUIDTable("students") {
    val schoolId = reference("school_id", Schools, onDelete = ReferenceOption.CASCADE)
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val createdBy = reference("created_by", Users)
    val version = long("version").default(1)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
}
