package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}

enum class EntityType {
    STUDENT,
    SCHOOL_CLASS
}

object SyncLog : LongIdTable("sync_log") {
    val schoolId = reference("school_id", Schools, onDelete = ReferenceOption.CASCADE)
    val entityType = varchar("entity_type", 50)
    val entityId = uuid("entity_id")
    val operation = enumerationByName<SyncOperation>("operation", 20)
    val userId = reference("user_id", Users)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
