package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object ParentStudentLinks : UUIDTable("parent_student_links") {
    val parentUserId = reference("parent_user_id", Users, onDelete = ReferenceOption.CASCADE)
    val studentId = reference("student_id", Students, onDelete = ReferenceOption.CASCADE)
    val status = varchar("status", 20)
    val createdBy = reference("created_by", Users)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)
    val revokedAt = timestampWithTimeZone("revoked_at").nullable()
}
