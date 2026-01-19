package de.aarondietz.lehrerlog.db.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone

object TaskSubmissions : Table("task_submissions") {
    val taskId = reference("task_id", Tasks)
    val studentId = reference("student_id", Students)
    val submittedAt = timestampWithTimeZone("submitted_at").defaultExpression(CurrentTimestampWithTimeZone)
    val createdAt = timestampWithTimeZone("created_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(taskId, studentId)
}
