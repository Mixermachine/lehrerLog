package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object Tasks : UUIDTable("tasks") {
    val schoolId = reference("school_id", Schools)
    val schoolClassId = reference("school_class_id", SchoolClasses)
    val title = varchar("title", 200)
    val description = text("description").nullable()
    val dueAt = timestampWithTimeZone("due_at")
    val createdBy = reference("created_by", Users)
    val version = long("version").default(1)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val updatedAt = timestampWithTimeZone("updated_at").defaultExpression(CurrentTimestampWithTimeZone)
}
