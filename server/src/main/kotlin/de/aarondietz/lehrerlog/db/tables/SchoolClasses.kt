package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object SchoolClasses : UUIDTable("school_classes") {
    val schoolId = reference("school_id", Schools, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 100)
    val alternativeName = varchar("alternative_name", 100).nullable()
    val createdBy = reference("created_by", Users)
    val version = long("version").default(1)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
}
