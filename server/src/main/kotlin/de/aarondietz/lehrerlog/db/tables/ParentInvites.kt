package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object ParentInvites : UUIDTable("parent_invites") {
    val studentId = reference("student_id", Students, onDelete = ReferenceOption.CASCADE)
    val codeHash = varchar("code_hash", 128)
    val expiresAt = timestampWithTimeZone("expires_at")
    val createdBy = reference("created_by", Users)
    val usedBy = reference("used_by", Users).nullable()
    val usedAt = timestampWithTimeZone("used_at").nullable()
    val status = varchar("status", 20)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
}
